package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Pro sinovi: yangi fermer ro'yxatdan o'tgach (onboarding tugagach) — tizim beradigan bepul obuna (TRIAL, summa 0).
 * Oddiy obuna bo'lgani uchun monitoring, ogohlantirishlar, limitlar alohida mantiqsiz ishlaydi.
 * @PreAuthorize YO'Q — bot va job chaqiradi; controller'ga ulanmasin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrialService {

    static final String REF_ENDING = "TRIAL_ENDING", REF_ENDED = "TRIAL_ENDED";
    private static final int REMIND_DAYS_BEFORE = 2;

    private final FarmerRepository farmerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final SubscriptionGrants grants;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final AuditService auditService;

    /**
     * Sinov yoqilgan, fermer hali sinov olmagan va faol obunasi (to'lov, taklif mukofoti) yo'q bo'lsa — beradi.
     * Yoqilishidan oldin ro'yxatdan o'tganlar bu yerga kelmaydi (faqat onboarding oxirida chaqiriladi).
     */
    @Transactional
    public Optional<SubscriptionGrants.Granted> grantIfEligible(long farmerId) {
        if (!settings.getBool(Setting.TRIAL_ENABLED)) return Optional.empty();
        // Qulf: /start ikki marta tez bosilsa ham bitta sinov
        FarmerEntity farmer = farmerRepository.findLockedById(farmerId).orElse(null);
        if (farmer == null) return Optional.empty();
        if (subscriptionRepository.existsByFarmerIdAndPaymentMethod(farmerId, PaymentMethod.TRIAL)) return Optional.empty();
        if (subscriptionRepository.lastActiveUntil(farmerId, LocalDate.now()) != null) return Optional.empty();
        String code = settings.getString(Setting.TRIAL_PLAN);
        // Oldindan tekshiramiz: grant() (MANDATORY) exception tashlasa — butun tranzaksiya rollback-only bo'lib qolardi
        boolean planOk = planRepository.findByCode(code)
                .filter(p -> p.isActive() && !p.isSystemPlan() && !p.isContactSales()).isPresent();
        if (!planOk) {
            log.warn("Pro sinovi berilmadi: trial.plan={} faol pullik tarif emas", code);
            return Optional.empty();
        }
        int days = settings.getInt(Setting.TRIAL_DAYS);
        SubscriptionGrants.Granted g = grants.grant(farmer, code, days, PaymentMethod.TRIAL, "Pro sinovi: " + days + " kun");
        auditService.event(AuditEvent.TRIAL_GRANTED).target("FARMER", farmerId)
                .detail("subscriptionId", g.subscriptionId()).detail("plan", code)
                .detail("validUntil", g.validUntil().toString()).record();
        return Optional.of(g);
    }

    /**
     * Eslatmalar (TrialReminderJob, kunduzi har soat): tugashiga ≤ 2 kun qolganda va tugagach — bir martadan
     * (notifications.ref bilan). Sinovdan keyin davom etadigan obunasi bor fermerga yuborilmaydi.
     */
    @Transactional
    public int sendReminders(LocalDate today) {
        boolean friends = settings.getBool(Setting.REFERRAL_ENABLED);
        String required = String.valueOf(settings.getInt(Setting.REFERRAL_REQUIRED));
        String rewardDays = String.valueOf(settings.getInt(Setting.REFERRAL_REWARD_DAYS));
        int sent = 0;
        for (SubscriptionEntity s : subscriptionRepository.findTrialsEnding(today.minusDays(3), today.plusDays(REMIND_DAYS_BEFORE))) {
            long farmerId = s.getFarmer().getId();
            boolean ended = s.getValidUntil().isBefore(today);
            String ref = ended ? REF_ENDED : REF_ENDING;
            if (notifications.sent(ref, s.getId())) continue;
            // To'lov qildi / mukofot oldi — sinov oxiridan keyin ham obunasi bor
            LocalDate last = subscriptionRepository.lastActiveUntil(farmerId, today);
            if (last != null && last.isAfter(s.getValidUntil())) continue;
            String key = (ended ? "trial.ended" : "trial.ending") + (friends ? ".friends" : "");
            notifications.enqueueMessageRef(farmerId, NotificationCategory.SYSTEM, ref, s.getId(), key,
                    s.getPlan().getName(), s.getValidUntil(), required, rewardDays);
            sent++;
        }
        return sent;
    }
}
