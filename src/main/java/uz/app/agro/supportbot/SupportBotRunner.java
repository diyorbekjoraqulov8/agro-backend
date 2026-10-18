package uz.app.agro.supportbot;

import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.MessageSource;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.feedback.FeedbackService;
import uz.app.agro.feedback.FeedbackStatus;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.file.FileSource;
import uz.app.agro.security.AuthUser;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Support botning getUpdates oqimi (asosiy botdan alohida token — 409 to'qnashuvi yo'q):
 * <ul>
 *   <li>/id — istalgan chatda: chat va foydalanuvchi id'si (guruh id'sini bilish uchun)</li>
 *   <li>/start link_&lt;kod&gt; — shaxsiy chatda: xodim Telegram'ini bog'lash (kod — admin panel → Profil)</li>
 *   <li>"//" bilan boshlangan xabar — xodimlarning ichki izohi, fermerga bormaydi</li>
 *   <li>guruhdagi fermer mavzusiga xodim yozdi → shu xodim nomidan javob (FeedbackService — @PreAuthorize ishlaydi)</li>
 *   <li>"✅ Yakunlash" tugmasi yoki /close — suhbatni yopish</li>
 * </ul>
 * Guruh xabarlarini olish uchun bot guruhda ADMIN bo'lishi kerak (privacy mode). Xato — xodimga mavzuning o'zida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank() and ${support-bot.polling:true}")
public class SupportBotRunner implements SmartLifecycle {

    private static final Locale UZ = Locale.forLanguageTag("uz");

    private final SupportBotClient client;
    private final SupportBotProperties props;
    private final SupportStore store;
    private final FeedbackService feedback;
    private final FileRegistry files;
    private final MessageSource messages;
    private final SupportLinkService links;
    private final SupportActions actions;
    private final SupportPlans plans;
    // Xodim bo'lmagan guruh a'zosiga "ID bog'lanmagan" — bir marta (har xabarga emas)
    private final Set<Long> warned = ConcurrentHashMap.newKeySet();
    private volatile boolean running;

    @Override
    public void start() {
        client.raw().setUpdatesListener(this::process,
                e -> log.warn("support bot getUpdates: {}", e.response() != null
                        ? e.response().errorCode() + " " + e.response().description() : e.getMessage()),
                new GetUpdates().timeout(50).allowedUpdates("message", "callback_query"));
        running = true;
        log.info("support bot long polling boshlandi (guruh {})", props.groupConfigured() ? props.groupId() : "sozlanmagan");
    }

    private int process(List<Update> updates) {
        for (Update u : updates) {
            try {
                if (u.message() != null) onMessage(u.message());
                else if (u.callbackQuery() != null) onCallback(u.callbackQuery());
            } catch (RuntimeException e) {
                log.error("support bot update {} ishlanmadi", u.updateId(), e);
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void onMessage(Message m) {
        if (m.from() == null || Boolean.TRUE.equals(m.from().isBot()) || m.chat() == null) return;
        String text = m.text() != null ? m.text().strip() : m.caption() != null ? m.caption().strip() : "";
        if (isCommand(text, "/id")) {
            client.send(m.chat().id(), m.messageThreadId(), text("support.id", String.valueOf(m.chat().id()),
                    String.valueOf(m.from().id())), null);
            return;
        }
        if (m.chat().type() == Chat.Type.Private) {
            onPrivate(m, text);
            return;
        }
        if (!props.groupConfigured() || m.chat().id() != props.groupId().longValue()) return;
        Long thread = m.messageThreadId();
        // General mavzu yoki xizmat xabari (mavzu ochildi/yopildi) — fermer suhbati emas
        if (thread == null || !Boolean.TRUE.equals(m.isTopicMessage()) || m.forumTopicCreated() != null) return;
        SupportStore.Topic topic = store.topic(props.groupId(), thread).orElse(null);
        if (topic == null) return;
        if (isCommand(text, "/tarif")) {
            plans.command(m, topic);
            return;
        }
        if (isCommand(text, "/close")) {
            if (topic.feedbackId() == null) reply(m, text("support.noActive"));
            else asStaff(m, me -> feedback.close(topic.feedbackId()));
            return;
        }
        if (text.startsWith("//")) return;                          // ichki izoh — fermerga emas
        if (plans.onText(m, topic, thread, text)) return;                  // tarif berish: kelishilgan summa — fermerga emas
        if (topic.feedbackId() == null) {
            reply(m, text("support.noActive"));
            return;
        }
        boolean photo = m.photo() != null && m.photo().length > 0;
        if (text.isEmpty() && !photo) return;                       // stiker, ovoz — hozircha qo'llanmaydi
        asStaff(m, me -> {
            // Rasm AVVAL: yuklanmasa suhbat egasi o'zgarib, javob esa saqlanmay qolmasin
            String hashId = null;
            if (photo) {
                // Eng kattasi oxirida; xodim fayli sifatida saqlanadi (FeedbackService faqat o'zi yuklagan faylni beradi)
                byte[] bytes = client.download(m.photo()[m.photo().length - 1].fileId())
                        .orElseThrow(() -> new BadRequestException("Rasm yuklanmadi — qayta yuboring"));
                hashId = files.store(bytes, "telegram.jpg", new FileRegistry.Uploader(FileSource.STAFF, me.id(), me.email())).hashId();
            }
            // Faqat navbatdagini oladi; boshqa operatordagi suhbatga manage huquqli yozsa — egasi o'zgarmaydi (admin panel kabi)
            if (topic.status() == FeedbackStatus.NEW) feedback.claim(topic.feedbackId());
            feedback.send(topic.feedbackId(), text.isEmpty() ? null : text, hashId);
            client.react(m.chat().id(), m.messageId(), "👍");
        });
    }

    private void onCallback(CallbackQuery cb) {
        String data = cb.data();
        if (data == null || cb.maybeInaccessibleMessage() == null) {
            client.answer(cb.id(), null);
            return;
        }
        long chatId = cb.maybeInaccessibleMessage().chat().id();
        if (!props.groupConfigured() || chatId != props.groupId().longValue()) {
            client.answer(cb.id(), null);
            return;
        }
        if (plans.isMine(data)) {
            // Fermer — tugma turgan mavzudan (callback_data'da emas): boshqa fermerga tarif yozdirib bo'lmaydi
            Long thread = cb.maybeInaccessibleMessage() instanceof Message msg ? msg.messageThreadId() : null;
            SupportStore.Topic topic = thread == null ? null : store.topic(props.groupId(), thread).orElse(null);
            if (topic == null) {
                client.answer(cb.id(), null);
                return;
            }
            plans.onCallback(cb, thread, topic);
            return;
        }
        if (!data.startsWith(SupportBridge.CLOSE)) {
            client.answer(cb.id(), null);
            return;
        }
        long feedbackId;
        try {
            feedbackId = Long.parseLong(data.substring(SupportBridge.CLOSE.length()));
        } catch (NumberFormatException e) {
            client.answer(cb.id(), null);
            return;
        }
        SupportStore.Staff staff = store.staff(cb.from().id()).orElse(null);
        if (staff == null) {
            client.answer(cb.id(), text("support.unknownStaff.short"));
            return;
        }
        String error = actions.runAs(staff, me -> feedback.close(feedbackId));
        client.answer(cb.id(), error == null ? text("support.closedToast") : error);
    }

    /** Xodim nomidan (Profil'dagi Telegram ID) — huquqlari @PreAuthorize bilan tekshiriladi; xato mavzuga yoziladi */
    private void asStaff(Message m, Consumer<AuthUser> action) {
        SupportStore.Staff staff = store.staff(m.from().id()).orElse(null);
        if (staff == null) {
            if (warned.add(m.from().id())) reply(m, text("support.unknownStaff"));
            return;
        }
        String error = actions.runAs(staff, action);
        if (error != null) reply(m, "⚠️ " + error);
    }

    private void reply(Message m, String text) {
        client.send(m.chat().id(), m.messageThreadId(), text, null);
    }

    /** Shaxsiy chat: /start link_<kod> yoki /link <kod> — Telegram'ni xodimga bog'lash (egalik — xabar shu akkauntdan) */
    private void onPrivate(Message m, String text) {
        String[] parts = text.split("\\s+", 2);
        String code = null;
        if (isCommand(text, "/start") && parts.length > 1 && parts[1].startsWith(SupportLinkService.START_PREFIX)) {
            code = parts[1].substring(SupportLinkService.START_PREFIX.length());
        } else if (isCommand(text, "/link") && parts.length > 1) {
            code = parts[1].trim();
        }
        if (code == null) {
            reply(m, text("support.private"));
            return;
        }
        long tg = m.from().id();
        String answer = switch (links.confirm(code.toUpperCase(Locale.ROOT), tg)) {
            case LINKED -> {
                warned.remove(tg);
                yield text("support.link.ok", links.emailOf(tg));
            }
            case TAKEN -> text("support.link.taken");
            case EXPIRED, USER_GONE -> text("support.link.expired");
        };
        reply(m, answer);
    }

    /** "/id" yoki "/id@dropy_support_bot" (guruhda bot nomi bilan) */
    private static boolean isCommand(String text, String command) {
        String first = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return first.equals(command) || first.startsWith(command + "@");
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, UZ);
    }

    @Override
    public void stop() {
        client.raw().removeGetUpdatesListener();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
