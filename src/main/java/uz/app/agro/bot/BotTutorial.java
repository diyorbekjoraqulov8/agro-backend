package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.onboarding.TutorialVideoService;

/**
 * Yo'riqnoma videosi (admin → Onboarding): yangi fermerga salomlashishdan keyin va ❓ Qo'llanma'da, fermer tilida.
 * Birinchi yuborishda fayl yuklanadi va Telegram file_id saqlanadi — keyingilarga darhol (qayta yuklamasdan).
 * Video yo'q yoki yuborilmadi — jim (asosiy oqim to'xtamaydi).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
class BotTutorial {

    private final TutorialVideoService videos;
    private final FileRegistry files;
    private final BotSender sender;
    private final BotMessages messages;
    private final TelegramProperties props;
    // Admin yuklagan videoni Telegram'ga oldindan yuklash — admin so'rovi va polling thread'i kutmasin
    private final java.util.concurrent.ExecutorService prewarm = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tutorial-prewarm");
        t.setDaemon(true);
        return t;
    });

    /**
     * Yangi video yuklandi → xodimlar guruhiga (TELEGRAM_SUPPORT_CHAT_ID) bir marta yuboramiz va file_id saqlaymiz:
     * birinchi fermerga 10 MB polling thread'ida yuklanmaydi. Guruh sozlanmagan — birinchi fermerda yuklanadi
     */
    @org.springframework.context.event.EventListener
    void onUploaded(TutorialVideoService.TutorialVideoUploaded e) {
        if (props.supportChatId() == null) return;
        prewarm.execute(() -> videos.forLanguage(e.language()).filter(v -> v.language().equals(e.language()))
                .filter(v -> v.telegramFileId() == null).ifPresent(v -> {
                    try {
                        String id = sender.sendVideo(props.supportChatId(), null, files.content(v.fileHashId()).bytes(),
                                "🎬 Yo'riqnoma videosi (" + v.language() + ") yangilandi — fermerlarga shu boradi", null);
                        if (id != null) videos.rememberTelegramId(v, id);
                    } catch (RuntimeException ex) {
                        log.warn("yo'riqnoma videosi oldindan yuklanmadi ({}): {}", v.language(), ex.toString());
                    }
                }));
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        prewarm.shutdownNow();
    }

    /** @return true — video yuborildi */
    boolean send(long chatId, String lang) {
        var v = videos.forLanguage(lang).orElse(null);
        if (v == null) return false;
        String caption = messages.text(lang, "tutorial.caption");
        if (v.telegramFileId() != null) {
            if (sender.sendVideo(chatId, v.telegramFileId(), null, caption, null) != null) return true;
            log.info("yo'riqnoma videosi file_id bilan ketmadi ({}), fayldan qayta yuklanadi", v.language());
        }
        try {
            String fileId = sender.sendVideo(chatId, null, files.content(v.fileHashId()).bytes(), caption, null);
            if (fileId == null) return false;
            videos.rememberTelegramId(v, fileId);
            return true;
        } catch (RuntimeException e) {                  // fayl diskda yo'q va h.k.
            log.warn("yo'riqnoma videosi yuborilmadi ({}): {}", v.language(), e.toString());
            return false;
        }
    }
}
