package uz.app.agro.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.problem.ProblemRecorder;
import uz.app.agro.problem.ProblemSource;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.notification.NotificationChannel.Delivery;
import uz.app.agro.notification.NotificationChannel.Recipient;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatcher'ning tranzaksiya chegaralari ALOHIDA bean'da (ADR #24): navbatdan olish (qisqa tranzaksiya) →
 * kanalga yuborish (tranzaksiyasiz, tarmoq) → natijani yozish (qisqa tranzaksiya).
 */
@Component
@RequiredArgsConstructor
class NotificationStore {

    /** Qayta urinish oraliqlari: 1, 5, 30 daqiqa, keyin har soat */
    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(1));
    private static final Duration STUCK_AFTER = Duration.ofMinutes(10);
    private static final int ERROR_MAX = 500;

    private final NotificationRepository repository;
    private final FarmerRepository farmerRepository;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final ProblemRecorder problems;

    /** Yuborishga tayyor xabar — tranzaksiyadan tashqarida ishlatiladi */
    record Job(long id, ChannelType channel, String template, Map<String, Object> params, Recipient to) {}

    @Transactional
    public int requeueStuck() {
        return repository.requeueStuck(LocalDateTime.now().minus(STUCK_AFTER));
    }

    /**
     * PENDING → SENDING. SKIP LOCKED faqat tranzaksiya ichida qulflaydi; commit'dan keyin status SENDING —
     * boshqa instans olmaydi. nextAttemptAt — "qachondan beri yuborilmoqda" (qotib qolganini aniqlash uchun).
     */
    @Transactional
    public List<Job> claim(Collection<ChannelType> channels, int limit) {
        List<NotificationEntity> batch = repository.lockNextBatch(channels.stream().map(Enum::name).toList(), limit);
        if (batch.isEmpty()) return List.of();
        Map<Long, FarmerEntity> farmers = farmerRepository.findAllById(batch.stream().map(NotificationEntity::getFarmerId).toList())
                .stream().collect(Collectors.toMap(FarmerEntity::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        return batch.stream().map(n -> {
            n.setStatus(NotificationStatus.SENDING);
            n.setAttempts(n.getAttempts() + 1);
            n.setNextAttemptAt(now);
            FarmerEntity f = farmers.get(n.getFarmerId());
            Recipient to = f == null ? null : new Recipient(f.getId(), f.getTelegramUserId(), f.getChatId(), f.getLanguage(), f.isBlockedBot(), f.isLab());
            return new Job(n.getId(), n.getChannel(), n.getTemplate(), new java.util.HashMap<>(n.getParams()), to);
        }).toList();
    }

    /** Fermer o'chirilgan yoki botni bloklagan — yubormaymiz (qayta urinish ham befoyda) */
    @Transactional
    public void skip(long id, String reason) {
        finish(id, NotificationStatus.SKIPPED, reason, true);
    }

    /** Lab dala (V41) — Telegram'i yo'q fermer: yuborilmaydi, lekin bu xato emas (muammo yozilmaydi) */
    @Transactional
    public void skipLab(long id) {
        finish(id, NotificationStatus.SKIPPED, "Lab dala — Telegram'ga yuborilmaydi", false);
    }

    @Transactional
    public void complete(long id, Delivery d) {
        NotificationEntity n = repository.findById(id).orElseThrow();
        switch (d.result()) {
            case SENT -> {
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(LocalDateTime.now());
                n.setLastError(null);
                published(n, true);
            }
            case FAILED -> finish(id, NotificationStatus.FAILED, d.error(), true);
            case RETRY -> {
                if (n.getAttempts() >= settings.getInt(Setting.NOTIFICATION_MAX_ATTEMPTS)) {
                    finish(id, NotificationStatus.FAILED, d.error(), true);
                } else {
                    n.setStatus(NotificationStatus.PENDING);
                    n.setLastError(cut(d.error()));
                    n.setNextAttemptAt(LocalDateTime.now().plus(BACKOFF.get(Math.min(n.getAttempts(), BACKOFF.size()) - 1)));
                }
            }
        }
    }

    private void finish(long id, NotificationStatus status, String error, boolean problem) {
        NotificationEntity n = repository.findById(id).orElseThrow();
        n.setStatus(status);
        n.setLastError(cut(error));
        published(n, false);
        if (!problem) return;
        // Yetmadi: FAILED — xato; SKIPPED (botni bloklagan, fermer yo'q) — fermer tomoni, ogohlantirish.
        // Kampaniyaning SKIPPED'lari yozilmaydi — bloklaganlar ro'yxati kampaniya hisobotida bor (sahifani to'ldirmasin)
        if (status == NotificationStatus.SKIPPED && n.getCampaignId() != null) return;
        var p = status == NotificationStatus.FAILED
                ? problems.error(ProblemSource.NOTIFICATION, "Xabar yetkazilmadi (" + n.getTemplate() + "): " + error)
                : problems.warn(ProblemSource.NOTIFICATION, "Xabar yuborilmadi (" + n.getTemplate() + "): " + error);
        p.farmer(n.getFarmerId()).ref("NOTIFICATION", n.getId()).record();
    }

    /** Ref egasi (masalan chat xabari) commit'dan keyin "yetkazildi/yetmadi" belgisini qo'yadi */
    private void published(NotificationEntity n, boolean delivered) {
        if (n.getRefType() != null) events.publishEvent(new NotificationDeliveredEvent(n.getId(), n.getRefType(), n.getRefId(), delivered));
    }

    private static String cut(String s) {
        return s == null ? null : s.length() <= ERROR_MAX ? s : s.substring(0, ERROR_MAX);
    }
}
