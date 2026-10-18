package uz.app.agro.supportbot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.app.agro.bot.BotFiles;
import uz.app.agro.common.HumanDates;
import uz.app.agro.feedback.FeedbackActivityEvent;
import uz.app.agro.feedback.FeedbackKind;
import uz.app.agro.feedback.FeedbackSender;
import uz.app.agro.file.FileRegistry;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongFunction;

/**
 * Murojaatlar → xodimlar guruhi (Topics): har fermerga bitta mavzu, yangi suhbatda kartochka, har xabar (rasm bilan)
 * ko'zgu. Admin paneldan yozilgan javob ham mavzuda ko'rinadi — ikkala joy bir xil. Guruhdan yozilgani qaytib
 * kelmaydi (FROM_TELEGRAM). Tarmoq — commit'dan keyin, bitta thread'da (tartib saqlanadi, bitta fermerga ikki mavzu yo'q).
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank()")
public class SupportBridge {

    /** Runner guruhdagi javobni saqlayotganda — o'sha xabarni guruhga qaytarmaslik uchun (AFTER_COMMIT shu thread'da) */
    static final ThreadLocal<Boolean> FROM_TELEGRAM = ThreadLocal.withInitial(() -> false);

    static final String CLOSE = "sb:close:";
    private static final Locale UZ = Locale.forLanguageTag("uz");

    private final SupportBotClient client;
    private final SupportBotProperties props;
    private final SupportStore store;
    private final MessageSource messages;
    private final FileRegistry files;
    private final ObjectProvider<BotFiles> farmerFiles;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "support-bridge");
        t.setDaemon(true);
        return t;
    });

    public SupportBridge(SupportBotClient client, SupportBotProperties props, SupportStore store, MessageSource messages,
                         FileRegistry files, ObjectProvider<BotFiles> farmerFiles) {
        this.client = client;
        this.props = props;
        this.store = store;
        this.messages = messages;
        this.files = files;
        this.farmerFiles = farmerFiles;
    }

    /** Guruh sozlangan — eski "yangi murojaat" xabari (TELEGRAM_SUPPORT_CHAT_ID) kerak emas */
    public boolean active() {
        return props.groupConfigured();
    }

    @TransactionalEventListener
    public void on(FeedbackActivityEvent e) {
        if (!active()) return;
        if (e.type() == FeedbackActivityEvent.Type.STAFF_MESSAGE && FROM_TELEGRAM.get()) return;
        executor.submit(() -> {
            try {
                handle(e);
            } catch (RuntimeException ex) {
                log.error("support guruhiga yozilmadi (murojaat {})", e.feedbackId(), ex);
            }
        });
    }

    private void handle(FeedbackActivityEvent e) {
        SupportStore.Post p = store.post(e.feedbackId(), e.messageId(), props.groupId()).orElse(null);
        if (p == null) return;
        Long topic = p.topicId();
        if (topic == null) {
            topic = openTopic(p);                                     // kartochka bilan
            if (topic == null) return;
        } else if (e.opened()) {
            client.renameTopic(props.groupId(), topic, topicName(p)); // tarif o'zgargan bo'lishi mumkin
            // Mavzu o'chirilgan bo'lsa openTopic kartochkani o'zi yuboradi — ikkinchi marta emas
            topic = deliver(p, topic, t -> client.send(props.groupId(), t, card(p), cardButtons(p)), false);
            if (topic == null) return;
        }
        switch (e.type()) {
            case FARMER_MESSAGE, STAFF_MESSAGE -> {
                if (p.message() != null) deliver(p, topic, t -> post(t, p), true);
            }
            // Yopildi xabari uchun o'chirilgan mavzuni qayta ochmaymiz — shunchaki yuborishga urinish
            case CLOSED -> client.send(props.groupId(), topic, text("support.closed", String.valueOf(p.feedbackId())), null);
        }
    }

    /**
     * Mavzuga yuborish; xodim mavzuni Telegram'da yopgan bo'lsa — qayta ochib, o'chirgan bo'lsa — yangisini (kartochka
     * bilan) ochib qayta yuboradi (resendInNew=false — action kartochkaning o'zi). @return ishlatilgan mavzu; null — ochilmadi
     */
    private Long deliver(SupportStore.Post p, long topic, LongFunction<SupportBotClient.Result> action, boolean resendInNew) {
        SupportBotClient.Result r = action.apply(topic);
        if (r.ok() || r.error() == null) return topic;
        String error = r.error().toLowerCase(Locale.ROOT);
        if (error.contains("topic_closed")) {
            client.reopenTopic(props.groupId(), topic);
            action.apply(topic);
            return topic;
        }
        if (error.contains("thread not found") || error.contains("topic_deleted") || error.contains("topic_id_invalid")) {
            Long fresh = openTopic(p);
            if (fresh != null && resendInNew) action.apply(fresh);
            return fresh;
        }
        return topic;                                                // boshqa xato (tarmoq) — log'da
    }

    /** Yangi mavzu: ochiladi, fermerga bog'lanadi, kartochka yuboriladi */
    private Long openTopic(SupportStore.Post p) {
        Optional<Long> id = client.createTopic(props.groupId(), topicName(p));
        if (id.isEmpty()) {
            log.warn("support mavzusi ochilmadi — guruhda Topics yoqilganmi va bot admin (Manage topics)mi?");
            return null;
        }
        store.saveTopic(p.farmerId(), props.groupId(), id.get());
        client.send(props.groupId(), id.get(), card(p), cardButtons(p));
        return id.get();
    }

    private SupportBotClient.Result post(long topic, SupportStore.Post p) {
        SupportStore.Msg m = p.message();
        String head = m.sender() == FeedbackSender.STAFF ? "👤 " + (m.staffEmail() == null ? "Xodim" : m.staffEmail()) + ":\n" : "";
        String body = head + (m.text() == null ? "" : m.text());
        byte[] photo = null;
        if (m.fileHashId() != null) {
            try {
                var c = files.content(m.fileHashId());
                if (c.info().contentType() != null && c.info().contentType().startsWith("image/")) {
                    photo = c.bytes();
                } else {
                    return client.sendDocument(props.groupId(), topic, c.bytes(), c.info().name(), c.info().contentType(), body.isBlank() ? null : body);
                }
            } catch (RuntimeException ex) {
                body += "\n📎 fayl — admin panelda";
            }
        }
        // Serverda saqlanmagan (eski) rasm — asosiy botdan yuklab olamiz
        if (photo == null && m.photoFileId() != null) {
            photo = farmerFiles.stream().findFirst().flatMap(f -> f.download(m.photoFileId())).orElse(null);
        }
        if (photo != null) return client.sendPhoto(props.groupId(), topic, photo, body.isBlank() ? null : body);
        return client.send(props.groupId(), topic, body.isBlank() ? "📎" : body, null);
    }

    /** "💎 Alisher Karimov · #12" — tarif belgisi: 🏢 cheksiz (Agro Biznes), 💎 pullik, 🆓 bepul */
    private String topicName(SupportStore.Post p) {
        String icon = p.paid() && p.unlimited() ? "🏢" : p.paid() ? "💎" : "🆓";
        String name = p.farmerName() != null && !p.farmerName().isBlank() ? p.farmerName()
                : p.username() != null ? "@" + p.username() : "Fermer";
        return icon + " " + name + " · #" + p.farmerId();
    }

    private String card(SupportStore.Post p) {
        String who = (p.farmerName() == null ? "—" : p.farmerName()) + (p.username() == null ? "" : " (@" + p.username() + ")")
                + (p.phone() == null ? "" : " · 📞 " + p.phone());
        String plan = p.planName() + (p.paid() && p.validUntil() != null ? " — " + HumanDates.format("uz", p.validUntil()) + "gacha" : "");
        String kind = text("btn.fb." + p.kind().name());
        return text("support.card", String.valueOf(p.feedbackId()), kind, who, plan, String.valueOf(p.fields()), ha(p.areaHa()))
                + (p.kind() == FeedbackKind.SUBSCRIPTION ? "\n\n" + text("support.card.sales") : "");
    }

    private InlineKeyboardMarkup cardButtons(SupportStore.Post p) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(
                new InlineKeyboardButton(text("support.btn.close")).callbackData(CLOSE + p.feedbackId()),
                new InlineKeyboardButton(text("support.btn.plan")).callbackData(SupportPlans.OPEN));
        if (props.adminUrl() != null && !props.adminUrl().isBlank()) {
            kb.addRow(new InlineKeyboardButton(text("support.btn.admin"))
                    .url(props.adminUrl().replaceAll("/+$", "") + "/feedback?tab=all&chat=" + p.feedbackId()));
        }
        return kb;
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, UZ);
    }

    private static String ha(BigDecimal v) {
        return v == null ? "0" : v.setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
