package uz.app.agro.referral;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.billing.PlanEntity;
import uz.app.agro.billing.PlanRepository;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * "Do'stlarni taklif qilish" — bot va tizim chaqiradi, @PreAuthorize YO'Q (xodim uchun — ReferralAdminService).
 * Qoida (Sozlamalar → Takliflar): har N ta FAOL do'st (birinchi tahlili tayyor) → M kun bepul tarif.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralService {

    public static final String START_PREFIX = "ref_";
    // 0/O, 1/I/L yo'q — qo'lda yozilganda adashmasin
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Pattern CODE = Pattern.compile("[A-Z2-9]{8}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FarmerRepository farmerRepository;
    private final ReferralRewardRepository rewardRepository;
    private final SettingsService settings;
    private final PlanRepository planRepository;
    private final ApplicationEventPublisher events;

    public record Summary(boolean enabled, String code, long invited, long activated, long rewards,
                          int required, int rewardDays, String rewardPlanName, int progress) {}

    /**
     * YANGI fermer /start ref_KOD bilan keldi — chaqiruvchining (ro'yxatdan o'tish) tranzaksiyasida, INSERT'dan oldin.
     * Noma'lum kod — jim e'tiborsiz (start parametri ishonchsiz kirish). O'zini taklif qila olmaydi: yangi fermerning
     * hali kodi yo'q (kod faqat "🎁" ochilganda yasaladi), mavjud fermerga esa attach chaqirilmaydi.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void attach(FarmerEntity newFarmer, String startParam) {
        if (startParam == null || !startParam.startsWith(START_PREFIX)) return;
        String code = startParam.substring(START_PREFIX.length()).toUpperCase();
        if (!CODE.matcher(code).matches()) return;
        farmerRepository.findByReferralCode(code)
                .filter(ref -> !ref.getId().equals(newFarmer.getId()))
                .ifPresent(ref -> {
                    newFarmer.setReferredById(ref.getId());
                    newFarmer.setReferredAt(LocalDateTime.now());
                    log.info("yangi fermer (tg {}) — {} taklifi bilan keldi", newFarmer.getTelegramUserId(), ref.getId());
                });
    }

    /** Birinchi tahlil tayyor — chaqiruvchining tranzaksiyasida; mukofot hisobi commit'dan keyin (event) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markActivated(FarmerEntity farmer) {
        if (farmer.getActivatedAt() != null) return;
        farmer.setActivatedAt(LocalDateTime.now());
        if (farmer.getReferredById() != null) {
            events.publishEvent(new ReferralActivatedEvent(farmer.getReferredById(), farmer.getId()));
        }
    }

    /** Bot "🎁 Do'stlar": kod (yo'q bo'lsa yasaladi) va hisob */
    @Transactional
    public Optional<Summary> summary(long telegramUserId) {
        return farmerRepository.findByTelegramUserId(telegramUserId).map(f -> {
            if (f.getReferralCode() == null) f.setReferralCode(newCode());
            int required = settings.getInt(Setting.REFERRAL_REQUIRED);
            long activated = farmerRepository.countByReferredByIdAndActivatedAtIsNotNull(f.getId());
            long used = rewardRepository.usedReferrals(f.getId());
            String planCode = settings.getString(Setting.REFERRAL_REWARD_PLAN);
            String planName = planRepository.findByCode(planCode).map(PlanEntity::getName).orElse(planCode);
            return new Summary(settings.getBool(Setting.REFERRAL_ENABLED), f.getReferralCode(),
                    farmerRepository.countByReferredById(f.getId()), activated, rewardRepository.countByFarmerId(f.getId()),
                    required, settings.getInt(Setting.REFERRAL_REWARD_DAYS), planName, (int) Math.max(0, activated - used));
        });
    }

    private String newCode() {
        // 31^8 ≈ 8.5·10^11 — to'qnashuv deyarli yo'q; bo'lsa ham qayta urinamiz (unique index himoya qiladi)
        for (int attempt = 0; attempt < 5; attempt++) {
            char[] c = new char[8];
            for (int i = 0; i < c.length; i++) c[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
            String code = new String(c);
            if (farmerRepository.findByReferralCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("taklif kodi yasalmadi");
    }
}
