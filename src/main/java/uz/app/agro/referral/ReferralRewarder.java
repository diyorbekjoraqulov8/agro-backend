package uz.app.agro.referral;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.billing.PaymentMethod;
import uz.app.agro.billing.SubscriptionGrants;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

/**
 * Do'st faollashdi → (tahlil tranzaksiyasi commit bo'lgach) mukofot hisobi YANGI tranzaksiyada; fermerga xabar —
 * shu tranzaksiyada outbox'ga (mukofot va xabar birga commit). Tahlil natijasi mukofot xatosiga bog'liq emas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ReferralRewarder {

    private final FarmerRepository farmerRepository;
    private final ReferralRewardRepository rewardRepository;
    private final SubscriptionGrants grants;
    private final SettingsService settings;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final ObjectProvider<ReferralRewarder> self;      // proxy orqali — REQUIRES_NEW ishlashi uchun

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivated(ReferralActivatedEvent e) {
        try {
            self.getObject().reward(e.referrerId(), e.friendId());
        } catch (RuntimeException ex) {
            // Masalan mukofot tarifi nofaol — mukofot keyingi faollashishda qayta hisoblanadi (faol do'stlar yo'qolmaydi);
            // fermer baribir do'sti faollashganini bilsin
            log.error("taklif mukofoti hisoblanmadi (referrer {}): {}", e.referrerId(), ex.getMessage());
            self.getObject().progressOnly(e.referrerId(), e.friendId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progressOnly(long referrerId, long friendId) {
        if (farmerRepository.findById(referrerId).isEmpty()) return;
        int required = settings.getInt(Setting.REFERRAL_REQUIRED);
        long free = farmerRepository.countByReferredByIdAndActivatedAtIsNotNull(referrerId) - rewardRepository.usedReferrals(referrerId);
        notifyProgress(referrerId, friendId, (int) Math.max(0, Math.min(free, required)), required);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reward(long referrerId, long friendId) {
        // Qulf: shu fermerning ikki do'sti bir vaqtda faollashsa ham hisob ketma-ket (ikki marta mukofot yo'q)
        FarmerEntity ref = farmerRepository.findLockedById(referrerId).orElse(null);
        if (ref == null) return;
        int required = settings.getInt(Setting.REFERRAL_REQUIRED);
        long free = farmerRepository.countByReferredByIdAndActivatedAtIsNotNull(referrerId) - rewardRepository.usedReferrals(referrerId);
        int days = settings.getInt(Setting.REFERRAL_REWARD_DAYS);
        int rewards = 0;
        if (settings.getBool(Setting.REFERRAL_ENABLED)) {
            String plan = settings.getString(Setting.REFERRAL_REWARD_PLAN);
            while (free >= required) {
                SubscriptionGrants.Granted g = grants.grant(ref, plan, days, PaymentMethod.REFERRAL,
                        "Taklif mukofoti: " + required + " ta faol do'st");
                ReferralRewardEntity r = new ReferralRewardEntity();
                r.setFarmerId(referrerId);
                r.setSubscriptionId(g.subscriptionId());
                r.setReferralsUsed(required);
                rewardRepository.save(r);
                auditService.event(AuditEvent.REFERRAL_REWARDED).target("FARMER", referrerId)
                        .detail("subscriptionId", g.subscriptionId()).detail("plan", plan)
                        .detail("validUntil", g.validUntil().toString()).record();
                // Raqamlar String — MessageFormat 30 ni "30" qoldirsin; sana LocalDate — NotificationService fermer tilida yozadi
                notifications.enqueueMessage(referrerId, NotificationCategory.REFERRAL, "ref.rewarded",
                        g.planName(), String.valueOf(days), g.validUntil());
                rewards++;
                free -= required;
            }
        }
        if (rewards == 0) notifyProgress(referrerId, friendId, (int) Math.max(0, free), required);
    }

    private void notifyProgress(long referrerId, long friendId, int progress, int required) {
        String name = friendName(friendId);
        // Ismsiz do'st — alohida matn (har tilda o'z grammatikasi; "do'stingiz" so'zini o'zbekcha qo'shib bo'lmaydi)
        if (name == null) {
            notifications.enqueueMessage(referrerId, NotificationCategory.REFERRAL, "ref.friendActivated.anon",
                    String.valueOf(progress), String.valueOf(required));
        } else {
            notifications.enqueueMessage(referrerId, NotificationCategory.REFERRAL, "ref.friendActivated",
                    name, String.valueOf(progress), String.valueOf(required));
        }
    }

    /** @return ism yoki @username; ikkalasi ham yo'q — null */
    private String friendName(long friendId) {
        FarmerEntity f = farmerRepository.findById(friendId).orElse(null);
        if (f == null) return null;
        if (f.getFullName() != null) return f.getFullName();
        return f.getTelegramUsername() != null ? "@" + f.getTelegramUsername() : null;
    }
}
