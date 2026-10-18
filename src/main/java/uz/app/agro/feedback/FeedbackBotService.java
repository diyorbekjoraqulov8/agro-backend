package uz.app.agro.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * FAQAT bot chaqiradi. @PreAuthorize YO'Q — bot thread'ida autentifikatsiya yo'q.
 * Controller'ga ULANMASIN: xodimlar uchun — FeedbackService.
 * Egalik har metodda: suhbat id'si callback_data'dan keladi (ishonchsiz) — begonasi "topilmadi".
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackBotService {

    /** 24 soatda bitta fermerdan yangi murojaat — spam va tasodifiy takrordan himoya */
    public static final int DAILY_LIMIT = 10;
    /** 24 soatda fermer xabarlari (barcha suhbatlar) — suhbatni to'xtatmaydigan, lekin floodni kesadigan chegara */
    public static final int DAILY_MESSAGE_LIMIT = 150;
    public static final int MAX_LENGTH = 4000;
    // \n va \t qoladi; NUL (0x00) PostgreSQL'da xato beradi, boshqa boshqaruv belgilari — axlat
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    private final FeedbackRepository feedbackRepository;
    private final ApplicationEventPublisher events;
    private final FeedbackMessageRepository messageRepository;
    private final FarmerRepository farmerRepository;

    public enum Outcome { SAVED, APPENDED, EMPTY, LIMIT, CLOSED, NOT_FOUND }

    /** farmerName/username — xodimlar guruhiga xabar uchun */
    public record Result(Outcome outcome, Long id, String farmerName, String farmerUsername) {
        static Result of(Outcome o) { return new Result(o, null, null, null); }
        static Result of(Outcome o, Long id) { return new Result(o, id, null, null); }
    }

    public record Active(long id, FeedbackStatus status) {}

    public record Closed(long id, boolean askRating) {}

    /**
     * Fermer yuborgan fayl: fileHashId — serverga saqlangan (files); telegramFileId — Telegram'dagi asl rasm
     * (yuklab bo'lmasa ham saqlanadi — fonda qayta urinadi, FeedbackFileBackfill).
     */
    public record Attachment(String fileHashId, String telegramFileId) {
        public Attachment {
            if (telegramFileId != null && (telegramFileId.isBlank() || telegramFileId.length() > 256)) telegramFileId = null;
        }
        boolean empty() { return fileHashId == null && telegramFileId == null; }
    }

    /** Fermerning ochiq suhbati (NEW yoki OPEN) */
    public Optional<Active> active(long telegramUserId) {
        return farmerRepository.findByTelegramUserId(telegramUserId)
                .flatMap(f -> feedbackRepository.findActive(f.getId()))
                .map(f -> new Active(f.getId(), f.getStatus()));
    }

    /** Yangi murojaat — birinchi xabar bilan. Ochiq suhbat bo'lsa unga qo'shiladi (bitta fermer — bitta suhbat) */
    @Transactional
    public Result submit(long telegramUserId, FeedbackKind kind, String text, Attachment file) {
        FarmerEntity farmer = farmerRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (farmer == null) return Result.of(Outcome.NOT_FOUND);
        String clean = clean(text);
        Attachment att = file == null || file.empty() ? null : file;
        if (clean == null && att == null) return Result.of(Outcome.EMPTY);
        Optional<FeedbackEntity> open = feedbackRepository.findActive(farmer.getId());
        if (open.isPresent()) {
            // Fermerda bitta ochiq suhbat: obuna so'rovi unga qo'shilsa ham — suhbat "Obuna so'rovlari" tabiga o'tadi
            if (kind == FeedbackKind.SUBSCRIPTION && open.get().getKind() != FeedbackKind.SUBSCRIPTION) {
                feedbackRepository.markSubscription(open.get().getId());
            }
            return append(farmer, open.get(), clean, att);
        }
        LocalDateTime now = LocalDateTime.now();
        if (feedbackRepository.countByFarmerIdAndCreatedDateAfter(farmer.getId(), now.minusDays(1)) >= DAILY_LIMIT) {
            return Result.of(Outcome.LIMIT);
        }
        FeedbackEntity f = new FeedbackEntity();
        f.setFarmer(farmer);
        f.setKind(kind);
        f.setSubject(clean == null ? (att.fileHashId() != null || att.telegramFileId() != null ? "📎" : "—") : clean.length() <= 200 ? clean : clean.substring(0, 199) + "…");
        f.setLastMessageAt(now);
        f.setLastSender(FeedbackSender.FARMER);
        f.setUnreadStaff(1);
        feedbackRepository.save(f);
        FeedbackMessageEntity m = messageRepository.save(farmerMessage(f.getId(), clean, att));
        events.publishEvent(FeedbackActivityEvent.farmer(f.getId(), m.getId(), true));
        return new Result(Outcome.SAVED, f.getId(), farmer.getFullName(), farmer.getTelegramUsername());
    }

    /** @param feedbackId null — ochiq suhbatga */
    @Transactional
    public Result append(long telegramUserId, Long feedbackId, String text, Attachment file) {
        FarmerEntity farmer = farmerRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (farmer == null) return Result.of(Outcome.NOT_FOUND);
        FeedbackEntity f = owned(farmer, feedbackId).orElse(null);
        if (f == null) return Result.of(Outcome.NOT_FOUND);
        return append(farmer, f, clean(text), file == null || file.empty() ? null : file);
    }

    private Result append(FarmerEntity farmer, FeedbackEntity f, String clean, Attachment att) {
        if (f.getStatus() == FeedbackStatus.CLOSED) return Result.of(Outcome.CLOSED, f.getId());
        if (clean == null && att == null) return Result.of(Outcome.EMPTY, f.getId());
        if (messageRepository.countFarmerMessagesSince(farmer.getId(), LocalDateTime.now().minusDays(1)) >= DAILY_MESSAGE_LIMIT) {
            return Result.of(Outcome.LIMIT, f.getId());
        }
        // Atomik: shu orada xodim yopgan bo'lsa 0 — xabar yopilgan suhbatga tushmaydi
        if (feedbackRepository.touchByFarmer(f.getId(), LocalDateTime.now()) == 0) return Result.of(Outcome.CLOSED, f.getId());
        FeedbackMessageEntity m = messageRepository.save(farmerMessage(f.getId(), clean, att));
        events.publishEvent(FeedbackActivityEvent.farmer(f.getId(), m.getId(), false));
        return new Result(Outcome.APPENDED, f.getId(), farmer.getFullName(), farmer.getTelegramUsername());
    }

    /** @param feedbackId null — ochiq suhbat. Bo'sh — topilmadi yoki allaqachon yopilgan */
    @Transactional
    public Optional<Closed> close(long telegramUserId, Long feedbackId) {
        FarmerEntity farmer = farmerRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (farmer == null) return Optional.empty();
        FeedbackEntity f = owned(farmer, feedbackId).orElse(null);
        if (f == null || f.getStatus() == FeedbackStatus.CLOSED) return Optional.empty();
        LocalDateTime now = LocalDateTime.now();
        f.setStatus(FeedbackStatus.CLOSED);
        f.setClosedAt(now);
        f.setClosedBy(FeedbackSender.FARMER);
        messageRepository.save(FeedbackStore.system(f.getId(), "Fermer suhbatni yakunladi"));
        events.publishEvent(FeedbackActivityEvent.closed(f.getId()));
        return Optional.of(new Closed(f.getId(), f.getAssigneeId() != null));
    }

    /** Faqat yopilgan, operator qatnashgan va hali baholanmagan suhbat. @return true — saqlandi */
    @Transactional
    public boolean rate(long telegramUserId, long feedbackId, int stars) {
        if (stars < 1 || stars > 5) return false;
        FarmerEntity farmer = farmerRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (farmer == null) return false;
        FeedbackEntity f = owned(farmer, feedbackId).orElse(null);
        if (f == null || f.getStatus() != FeedbackStatus.CLOSED || f.getAssigneeId() == null || f.getRating() != null) return false;
        f.setRating((short) stars);
        f.setRatedAt(LocalDateTime.now());
        return true;
    }

    private Optional<FeedbackEntity> owned(FarmerEntity farmer, Long feedbackId) {
        if (feedbackId == null) return feedbackRepository.findActive(farmer.getId());
        return feedbackRepository.findById(feedbackId).filter(f -> f.getFarmer().getId().equals(farmer.getId()));
    }

    private static FeedbackMessageEntity farmerMessage(Long feedbackId, String text, Attachment att) {
        FeedbackMessageEntity m = new FeedbackMessageEntity();
        m.setFeedbackId(feedbackId);
        m.setSender(FeedbackSender.FARMER);
        m.setText(text);
        if (att != null) {
            m.setFileHashId(att.fileHashId());
            m.setPhotoFileId(att.telegramFileId());
        }
        return m;
    }

    /** null — bo'sh. Boshqaruv belgilari olib tashlanadi, uzun matn kesiladi */
    static String clean(String text) {
        if (text == null) return null;
        String c = CONTROL.matcher(text).replaceAll("").strip();
        if (c.isEmpty()) return null;
        return c.length() <= MAX_LENGTH ? c : c.substring(0, MAX_LENGTH);
    }
}
