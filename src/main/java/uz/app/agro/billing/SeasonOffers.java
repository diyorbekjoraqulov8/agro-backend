package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.billing.dto.SeasonOffer;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Mavsumiy obuna (Sozlamalar → "Mavsumiy obuna"): sotuv davrida fermer mavsum oxirigacha bitta to'lov bilan oladi.
 * Davr — bugundan (joriy obuna bo'lsa — uning oxiridan keyin) mavsum oxirigacha; narx = tarif narxi × kunlar / muddat
 * (PROPORSIONAL — yuqoriga yaxlitlansa qolgan 31 kun uchun 2 oy to'lardi) × (100 − foiz)%. Qolgan davr bitta muddatdan
 * qisqa bo'lsa — taklif yo'q (oddiy obuna yaxshiroq). Cheksiz tarif (Agro Biznes) egasiga taklif yo'q — bot, support guruhi
 * va admin uchun bir xil (filtr shu yerda). Chegirmalar (DiscountPricing) bilan qo'shilmaydi.
 * @PreAuthorize YO'Q — bot va support guruhi chaqiradi; controller'ga to'g'ridan-to'g'ri ULANMASIN (xodimlar — DiscountService).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonOffers {

    private final SettingsService settings;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final PlanService planService;

    /** Davr kunlari (from..end inklyuziv) — SettingsService.seasonWarning ham shu bilan hisoblaydi */
    public static long periodDays(LocalDate from, LocalDate end) {
        return ChronoUnit.DAYS.between(from, end) + 1;
    }

    public Optional<SeasonOffer> offer(long farmerId, LocalDate today) {
        if (!settings.getBool(Setting.SEASON_ENABLED)) return Optional.empty();
        LocalDate saleFrom = settings.getDate(Setting.SEASON_SALE_FROM);
        LocalDate end = settings.getDate(Setting.SEASON_END);
        if (today.isBefore(saleFrom) || !today.isBefore(end)) return Optional.empty();
        PlanEntity p = plans.findByCode(settings.getString(Setting.SEASON_PLAN))
                .filter(x -> x.isActive() && !x.isSystemPlan() && !x.isContactSales() && x.getPrice().signum() > 0)
                .orElse(null);
        if (p == null || p.getDurationDays() == null) return Optional.empty();
        if (planService.effective(farmerId, today).unlimited()) return Optional.empty();
        LocalDate last = subscriptions.lastActiveUntil(farmerId, today);
        LocalDate from = last != null ? last.plusDays(1) : today;
        if (from.isAfter(end)) return Optional.empty();                  // obunasi mavsum oxiridan ham uzoq
        long days = periodDays(from, end);
        if (days < p.getDurationDays()) return Optional.empty();         // tarif muddatidan kam qoldi — oddiy obuna arzonroq
        int pct = settings.getInt(Setting.SEASON_DISCOUNT);
        int scale = "UZS".equals(p.getCurrency()) ? 0 : 2;
        BigDecimal base = p.getPrice().multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(p.getDurationDays()), scale, RoundingMode.HALF_UP);
        BigDecimal finalPrice = base.multiply(BigDecimal.valueOf(100L - pct))
                .divide(BigDecimal.valueOf(100), scale, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new SeasonOffer(p.getId(), p.getName(), from, end, (int) days, pct,
                base.setScale(2, RoundingMode.HALF_UP), finalPrice, p.getCurrency()));
    }
}
