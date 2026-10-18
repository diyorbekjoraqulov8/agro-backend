package uz.app.agro.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetMyDescription;
import com.pengrad.telegrambot.request.GetMyName;
import com.pengrad.telegrambot.request.GetMyShortDescription;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.request.SetMyDescription;
import com.pengrad.telegrambot.request.SetMyName;
import com.pengrad.telegrambot.request.SetMyShortDescription;
import lombok.RequiredArgsConstructor;
import uz.app.agro.problem.ProblemRecorder;
import uz.app.agro.problem.ProblemSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Long polling (webhook emas): kirish endpoint'i yo'q → yangi SecurityFilterChain kerak emas.
 * ⚠️ Bitta token bilan FAQAT bitta jarayon poll qilsin — ikkinchisi 409 Conflict oladi va
 * update'lar ikkiga bo'linadi. Dev va prod uchun ALOHIDA bot.
 * telegram.polling=false — instans faqat YUBORADI (hisobot, xabar), update olmaydi:
 * bir nechta instansda faqat bittasi poll qiladi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"telegram.enabled", "telegram.polling"}, havingValue = "true", matchIfMissing = false)
public class BotRunner implements SmartLifecycle {

    private final TelegramBot bot;
    private final BotUpdateHandler handler;
    private final BotMessages messages;
    private final ProblemRecorder problems;
    private volatile boolean running;
    // Oxirgi getUpdates xatosi (admin "Muammolar" holati): 409 — ikkinchi poller, tarmoq — vaqtincha
    private volatile java.time.Instant pollErrorAt;
    private volatile String pollError;

    @Override
    public void start() {
        registerCommands();
        registerProfile();
        bot.setUpdatesListener(this::process, e -> {
            pollErrorAt = java.time.Instant.now();
            if (e.response() != null && e.response().errorCode() == 409) {
                log.error("telegram 409: shu token bilan BOSHQA jarayon ham poll qilyapti (IntelliJ + terminal?)");
                pollError = "409: shu token bilan boshqa jarayon ham ishlayapti — xabarlar yo'qolishi mumkin";
                problems.error(ProblemSource.SYSTEM, "Telegram 409: shu bot tokeni bilan BOSHQA jarayon ham poll qilyapti").record();
            } else if (e.response() != null) {
                log.warn("telegram getUpdates {}: {}", e.response().errorCode(), e.response().description());
                pollError = "getUpdates " + e.response().errorCode() + ": " + e.response().description();
            } else {
                log.warn("telegram getUpdates tarmoq xatosi: {}", e.getMessage());
                pollError = "Telegram'ga ulanib bo'lmadi: " + e.getMessage();
            }
        }, new GetUpdates().timeout(50).allowedUpdates("message", "callback_query"));
        running = true;
        log.info("telegram bot long polling boshlandi");
    }

    private int process(java.util.List<Update> updates) {
        for (Update u : updates) {
            try {
                handler.handle(u);
            } catch (Exception e) {
                // Bitta buzuq update butun paketni to'xtatmasin; qayta urinilmaydi (cheksiz sikl bo'lmasin)
                log.error("update {} ishlanmadi", u.updateId(), e);
                problems.error(ProblemSource.BOT, "Bot xabarni ishlay olmadi: " + e)
                        .telegramUser(senderId(u)).cause(e).record();
                handler.apologize(u);
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    /** Oxirgi 5 daqiqadagi polling xatosi (admin holati uchun) */
    public java.util.Optional<String> recentPollError() {
        var at = pollErrorAt;
        return at != null && at.isAfter(java.time.Instant.now().minusSeconds(300)) ? java.util.Optional.ofNullable(pollError)
                : java.util.Optional.empty();
    }

    private static Long senderId(Update u) {
        if (u.message() != null && u.message().from() != null) return u.message().from().id();
        if (u.callbackQuery() != null && u.callbackQuery().from() != null) return u.callbackQuery().from().id();
        return null;
    }

    /** Telegram'dagi "Menu" — har til uchun. Xato bo'lsa bot baribir ishlaydi. */
    private void registerCommands() {
        for (String lang : BotMessages.LANGUAGES) {
            var req = new SetMyCommands(
                    new BotCommand("start", messages.text(lang, "cmd.start")),
                    new BotCommand("support", messages.text(lang, "cmd.support")),
                    new BotCommand("lang", messages.text(lang, "cmd.lang")),
                    new BotCommand("help", messages.text(lang, "cmd.help")));
            if (!lang.equals("en")) req.languageCode(lang);   // en — default (til ko'rsatilmagan)
            try {
                var r = bot.execute(req);
                if (!r.isOk()) log.warn("setMyCommands {}: {}", lang, r.description());
            } catch (RuntimeException e) {
                log.warn("setMyCommands {}: {}", lang, e.getMessage());
            }
        }
    }

    /**
     * Nom, qisqa tavsif (profil, havola ko'rinishi) va tavsif ("Bu bot nima qila oladi?" — /start'dan
     * oldin bo'sh chatda). Faqat FARQ bo'lsa yoziladi: Telegram bu metodlarga qattiq limit qo'yadi
     * (429), har restart'da qayta yozish shart emas. en — default (til ko'rsatilmagan).
     */
    private void registerProfile() {
        for (String lang : BotMessages.LANGUAGES) {
            String code = lang.equals("en") ? null : lang;
            try {
                String name = messages.text(lang, "profile.name");
                var gn = new GetMyName();
                if (code != null) gn.languageCode(code);
                if (!name.equals(bot.execute(gn).botName().name())) {
                    var req = new SetMyName().name(name);
                    if (code != null) req.languageCode(code);
                    logProfile("name", lang, bot.execute(req));
                }
                String shortText = messages.text(lang, "profile.short");
                var gs = new GetMyShortDescription();
                if (code != null) gs.languageCode(code);
                if (!shortText.equals(bot.execute(gs).botShortDescription().shortDescription())) {
                    var req = new SetMyShortDescription().description(shortText);
                    if (code != null) req.languageCode(code);
                    logProfile("short_description", lang, bot.execute(req));
                }
                String desc = messages.text(lang, "profile.description");
                var gd = new GetMyDescription();
                if (code != null) gd.languageCode(code);
                if (!desc.equals(bot.execute(gd).botDescription().description())) {
                    var req = new SetMyDescription().description(desc);
                    if (code != null) req.languageCode(code);
                    logProfile("description", lang, bot.execute(req));
                }
            } catch (RuntimeException e) {
                log.warn("bot profili [{}] yangilanmadi: {}", lang, e.getMessage());
            }
        }
    }

    private static void logProfile(String what, String lang, com.pengrad.telegrambot.response.BaseResponse r) {
        if (r.isOk()) log.info("bot profili yangilandi: {} [{}]", what, lang);
        else log.warn("bot profili {} [{}]: {} {}", what, lang, r.errorCode(), r.description());
    }

    @Override
    public void stop() {
        bot.removeGetUpdatesListener();
        running = false;
        log.info("telegram bot to'xtatildi");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
