package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.monitoring.FieldSnapshotService;
import uz.app.agro.monitoring.FieldSnapshotService.Snapshot;
import uz.app.agro.monitoring.FieldSnapshotService.Status;
import uz.app.agro.problem.ProblemRecorder;
import uz.app.agro.problem.ProblemSource;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * R4: "📸 Bugungi rasm" (fn:<dala>) va "📍 Borib ko'rish" (fg:<dala>). Python ~10–30 s — polling thread'ida EMAS
 * (boshqa fermerlar kutib qolardi): alohida kichik pool (2 thread, navbat 20; to'lsa — "biroz kuting").
 * Bir dala uchun bir vaqtda bitta so'rov.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
class BotSnapshot {


    private final FieldSnapshotService snapshots;
    private final FileRegistry files;
    private final BotSender sender;
    private final BotMessages messages;
    private final ProblemRecorder problems;

    /** Python snapshot.MIN_CROP_NDVI bilan bir xil */
    private static final double BARE_NDVI = 0.3;

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20),
            r -> { Thread t = new Thread(r, "snapshot"); t.setDaemon(true); return t; });

    void request(long tg, long chatId, String lang, long fieldId) {
        if (!snapshots.owns(tg, fieldId)) {
            sender.send(chatId, messages.text(lang, "field.notFound"), null);
            return;
        }
        if (!inFlight.add(fieldId)) {
            sender.send(chatId, messages.text(lang, "snapshot.busy"), null);
            return;
        }
        if (!allow(tg)) {
            inFlight.remove(fieldId);
            sender.send(chatId, messages.text(lang, "snapshot.limit"), null);
            return;
        }
        try {
            sender.send(chatId, messages.text(lang, "snapshot.wait"), null);   // rasmdan OLDIN (keshdan darhol kelishi mumkin)
            pool.execute(() -> run(tg, chatId, lang, fieldId));
        } catch (RejectedExecutionException e) {
            inFlight.remove(fieldId);
            sender.send(chatId, messages.text(lang, "snapshot.busy"), null);
        }
    }

    /** Fermer boshiga soatiga LIMIT ta (keshdan kelganlari ham — oddiy, adolatli) */
    private static final int LIMIT = 10;
    private final java.util.Map<Long, java.util.Deque<java.time.Instant>> recent = new ConcurrentHashMap<>();

    private boolean allow(long tg) {
        java.time.Instant now = java.time.Instant.now(), from = now.minus(java.time.Duration.ofHours(1));
        var q = recent.computeIfAbsent(tg, k -> new java.util.ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(from)) q.pollFirst();
            if (q.size() >= LIMIT) return false;
            q.addLast(now);
            return true;
        }
    }

    void goWeak(long tg, long chatId, String lang, long fieldId) {
        snapshots.latest(tg, fieldId).ifPresentOrElse(s -> {
            if (s.weakLat() == null) {              // eski xabardagi tugma — rasm yangilangan, sust joy yo'q
                sender.send(chatId, messages.text(lang, "snapshot.noWeakNow"), null);
                return;
            }
            sender.sendLocation(chatId, s.weakLat(), s.weakLon());
            sender.send(chatId, messages.text(lang, "snapshot.go"), null);
        }, () -> sender.send(chatId, messages.text(lang, "field.notFound"), null));
    }

    private void run(long tg, long chatId, String lang, long fieldId) {
        try {
            MDC.put("userId", "tg:" + tg);
            var r = snapshots.get(tg, fieldId);
            switch (r.status()) {
                case OK -> send(chatId, lang, fieldId, r.snapshot());
                case NONE -> sender.send(chatId, messages.text(lang, "snapshot.none"), null);
                case NOT_FOUND -> sender.send(chatId, messages.text(lang, "field.notFound"), null);
                case FAILED -> sender.send(chatId, messages.text(lang, "snapshot.failed"), null);
            }
        } catch (RuntimeException e) {
            log.warn("snapshot dala {}: {}", fieldId, e.toString());
            problems.error(ProblemSource.MONITORING, "Bugungi rasm yasalmadi: " + e).field(fieldId).cause(e).record();
            sender.send(chatId, messages.text(lang, "snapshot.failed"), null);
        } finally {
            inFlight.remove(fieldId);
            MDC.clear();
        }
    }

    private void send(long chatId, String lang, long fieldId, Snapshot s) {
        // Ekin yo'q (o'rim/shudgor) — Python sust joyni ataylab hisoblamaydi; "sust joy yo'q" deyish chalg'itadi
        String extra = s.weakLat() != null
                ? messages.text(lang, "snapshot.weak", String.valueOf(Math.round(s.weakPct())))
                : s.ndvi() < BARE_NDVI ? messages.text(lang, "snapshot.bare") : messages.text(lang, "snapshot.noWeak");
        String caption = messages.text(lang, "snapshot.caption", s.fieldName(), BotMessages.date(lang, s.sceneDate()), extra);
        InlineKeyboardMarkup kb = s.weakLat() == null ? null : new InlineKeyboardMarkup(
                new InlineKeyboardButton(messages.text(lang, "btn.goWeak")).callbackData(BotKeyboards.FIELD_GO_WEAK + fieldId));
        if (s.telegramFileId() != null && sender.sendPhotoId(chatId, s.telegramFileId(), null, caption, kb) != null) return;
        String fileId = sender.sendPhotoId(chatId, null, files.content(s.fileHashId()).bytes(), caption, kb);
        if (fileId != null) snapshots.rememberTelegramId(s.id(), fileId);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
