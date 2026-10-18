package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.billing.dto.PlanPrice;
import uz.app.agro.billing.dto.PlanResponse;
import uz.app.agro.common.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fermer uchun tarif narxi — mos chegirmalarning ENG foydalisi (qo'shilmaydi). Bot, support guruhi va to'lov yozish
 * shu yerdan. @PreAuthorize YO'Q — controller'ga to'g'ridan-to'g'ri ulanmasin (xodimlar uchun — DiscountService).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscountPricing {

    /**
     * Haqiqiy to'lov usullari — "birinchi to'lov" va "obunasi tugagan" shu bo'yicha (sinov va taklif mukofoti hisoblanmaydi).
     * Faol mukofot (REFERRAL) obunasi — PAID: fermer hozir Pro'dan foydalanyapti
     */
    static final Set<PaymentMethod> PAYMENTS = EnumSet.of(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER, PaymentMethod.OTHER);

    private final DiscountRepository discounts;
    private final SubscriptionRepository subscriptions;

    public DiscountAudience audience(long farmerId, LocalDate today) {
        List<SubscriptionEntity> active = subscriptions.findActiveOn(farmerId, today);
        if (active.stream().anyMatch(s -> s.getPaymentMethod() != PaymentMethod.TRIAL)) return DiscountAudience.PAID;
        if (!active.isEmpty()) return DiscountAudience.TRIAL;
        return paid(farmerId) ? DiscountAudience.EXPIRED : DiscountAudience.FREE;
    }

    /** Narxli pullik tariflar (bepul, "narx kelishiladi", 0 so'm — chegirma qo'llanmaydi) */
    public Map<Long, PlanPrice> prices(long farmerId, List<PlanResponse> plans, LocalDate today) {
        List<DiscountEntity> active = discounts.findActiveOn(today);
        DiscountAudience audience = active.isEmpty() ? null : audience(farmerId, today);
        boolean neverPaid = !active.isEmpty() && active.stream().anyMatch(d -> d.getKind() == DiscountKind.FIRST_PAYMENT)
                && !paid(farmerId);
        Map<Long, PlanPrice> out = new LinkedHashMap<>();
        for (PlanResponse p : plans) {
            out.put(p.id(), best(p, active, audience, neverPaid));
        }
        return out;
    }

    private boolean paid(long farmerId) {
        return subscriptions.existsByFarmerIdAndStatusAndPaymentMethodIn(farmerId, SubscriptionEntity.Status.ACTIVE, PAYMENTS);
    }

    public PlanPrice price(long farmerId, PlanResponse plan, LocalDate today) {
        return prices(farmerId, List.of(plan), today).get(plan.id());
    }

    private static PlanPrice best(PlanResponse p, List<DiscountEntity> active, DiscountAudience audience, boolean neverPaid) {
        PlanPrice none = new PlanPrice(p.id(), p.price(), p.price(), p.currency(), null, null, null, null);
        if (p.systemPlan() || p.contactSales() || p.price().signum() <= 0 || audience == null) return none;
        Optional<DiscountEntity> best = active.stream()
                .filter(d -> d.audienceSet().contains(audience))
                .filter(d -> d.planIdSet().isEmpty() || d.planIdSet().contains(p.id()))
                .filter(d -> d.getKind() != DiscountKind.FIRST_PAYMENT || neverPaid)
                .filter(d -> d.applicableTo(p.currency()))
                // Narxni 0 ga tushiradigan chegirma qo'llanmaydi (bepul pullik obuna — xato sozlama)
                .filter(d -> d.apply(p.price(), p.currency()).signum() > 0)
                .min(Comparator.comparing((DiscountEntity d) -> d.apply(p.price(), p.currency()))
                        .thenComparing(d -> d.getEndsOn() == null ? LocalDate.MAX : d.getEndsOn()));
        if (best.isEmpty()) return none;
        DiscountEntity d = best.get();
        BigDecimal finalPrice = d.apply(p.price(), p.currency());
        if (finalPrice.compareTo(p.price()) >= 0) return none;
        return new PlanPrice(p.id(), p.price(), finalPrice, p.currency(), d.getId(), d.getName(), label(d, p.currency()), d.getEndsOn());
    }

    /** "−20%" yoki "−10 000" (valyuta matnda alohida) */
    static String label(DiscountEntity d, String currency) {
        if (d.getValueType() == DiscountEntity.ValueType.PERCENT) {
            return "−" + d.getValue().stripTrailingZeros().toPlainString() + "%";
        }
        return "−" + Money.digits(d.getValue());
    }
}
