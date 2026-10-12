package uz.app.agro.feedback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;

import java.util.Optional;

/**
 * Eski xabarlardagi Telegram rasmlarini (photo_file_id) serverdagi papkaga ko'chiradi — ishga tushganda,
 * fonda (startup'ni kutdirmaydi). Fermer rasmini yuklab bo'lmagan yangi xabarlar ham keyingi ishga tushishda olinadi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FeedbackFileBackfill {

    private static final int BATCH = 200;

    private final FeedbackMessageRepository messages;
    private final FileRegistry files;
    private final ObjectProvider<FeedbackGateway> gateway;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        FeedbackGateway g = gateway.getIfAvailable();
        if (g == null) return;                                    // bot o'chiq — Telegram'dan yuklab bo'lmaydi
        Thread.ofVirtual().name("feedback-file-backfill").start(() -> run(g));
    }

    private void run(FeedbackGateway g) {
        int ok = 0, failed = 0;
        for (FeedbackMessageEntity m : messages.findLegacyPhotos(PageRequest.of(0, BATCH))) {
            try {
                Optional<byte[]> bytes = g.download(m.getPhotoFileId());
                if (bytes.isEmpty()) {
                    failed++;
                    continue;
                }
                String hashId = files.store(bytes.get(), "telegram-photo.jpg", FileRegistry.Uploader.telegram()).hashId();
                if (messages.attachFile(m.getId(), hashId) == 1) ok++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("xabar {} rasmi ko'chirilmadi: {}", m.getId(), e.getMessage());
            }
        }
        if (ok + failed > 0) log.info("eski Telegram rasmlari serverga ko'chirildi: {} ta, xato: {}", ok, failed);
    }
}
