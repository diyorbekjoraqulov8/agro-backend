package uz.app.agro.farmer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.field.Crop;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * FAQAT bot chaqiradi. @PreAuthorize YO'Q — bot thread'ida autentifikatsiya yo'q.
 * Controller'ga ULANMASIN: xodimlar uchun — FarmerService.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmerRegistrationService {

    private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    // Telegram ismida har narsa bo'lishi mumkin; NUL (0x00) PostgreSQL'da xato beradi
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private final FarmerRepository farmerRepository;
    private final uz.app.agro.referral.ReferralService referrals;
    private final AuditService auditService;

    public Optional<FarmerSnapshot> find(long telegramUserId) {
        return farmerRepository.findByTelegramUserId(telegramUserId).map(f -> FarmerSnapshot.of(f, false));
    }

    /**
     * /start: yangi fermer — yaratiladi; mavjud — profil yangilanadi.
     * source (reklama kanali) faqat BIRINCHI marta yoziladi — keyingi reklama atributsiyani buzmasin.
     */
    @Transactional
    public FarmerSnapshot start(TelegramProfile p, String startParam, String language) {
        Optional<FarmerEntity> existing = farmerRepository.findByTelegramUserId(p.telegramUserId());
        FarmerEntity f = existing.orElseGet(FarmerEntity::new);
        boolean created = existing.isEmpty();
        if (created) {
            f.setTelegramUserId(p.telegramUserId());
            f.setLanguage(language);
            f.setSource(startParam != null && SOURCE.matcher(startParam).matches() ? startParam : null);
            // INSERT'dan OLDIN: referred_by_id — updatable=false (keyin o'zgartirib bo'lmaydi)
            referrals.attach(f, startParam);
        }
        f.setChatId(p.chatId());
        f.setTelegramUsername(clean(p.username(), 64));
        f.setFullName(clean(join(p.firstName(), p.lastName()), 160));
        f.setBlockedBot(false);                       // /start yozdi — demak bloklamagan
        farmerRepository.save(f);

        if (created) {
            auditService.event(AuditEvent.FARMER_REGISTERED)
                    .target("FARMER", f.getId())
                    .detail("telegramUserId", p.telegramUserId())
                    .detail("source", f.getSource())
                    .record();
        }
        return FarmerSnapshot.of(f, created);
    }

    @Transactional
    public FarmerSnapshot setCrop(long telegramUserId, Crop crop) {
        return update(telegramUserId, f -> f.setMainCrop(crop));
    }

    @Transactional
    public FarmerSnapshot setIrrigation(long telegramUserId, IrrigationType type) {
        return update(telegramUserId, f -> f.setIrrigationType(type));
    }

    @Transactional
    public FarmerSnapshot setWaterSource(long telegramUserId, WaterSource source) {
        return update(telegramUserId, f -> f.setWaterSource(source));
    }

    @Transactional
    public FarmerSnapshot setLanguage(long telegramUserId, String language) {
        return update(telegramUserId, f -> f.setLanguage(language));
    }

    /** Telegram 403 (bot bloklangan) — keyingi xabarlar yuborilmasin. */
    @Transactional
    /** Telegram 403 dan keyin — bildirishnoma kanali qayta urinmasin */
    public boolean isBlocked(long chatId) {
        return farmerRepository.findByChatId(chatId).map(FarmerEntity::isBlockedBot).orElse(false);
    }

    public void markBlocked(long chatId) {
        farmerRepository.findByChatId(chatId).ifPresent(f -> f.setBlockedBot(true));
    }

    private FarmerSnapshot update(long telegramUserId, Consumer<FarmerEntity> change) {
        FarmerEntity f = farmerRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("fermer yo'q: tg " + telegramUserId));
        change.accept(f);
        return FarmerSnapshot.of(f, false);
    }

    private static String join(String a, String b) {
        String s = ((a == null ? "" : a) + " " + (b == null ? "" : b)).trim();
        return s.isEmpty() ? null : s;
    }

    private static String clean(String s, int max) {
        if (s == null) return null;
        String c = CONTROL.matcher(s).replaceAll("").strip();
        if (c.isEmpty()) return null;
        // code point bo'yicha: substring emoji'ni (surrogate juft) o'rtasidan kesib qo'yadi
        return c.codePointCount(0, c.length()) <= max ? c : c.substring(0, c.offsetByCodePoints(0, max));
    }
}
