package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.farmer.FarmerEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tizim beradigan obuna (taklif mukofoti, keyin — avtomatik to'lov). @PreAuthorize YO'Q — xodim emas, tizim chaqiradi;
 * controller'ga ULANMASIN (xodim uchun — BillingService.recordPayment). Chaqiruvchining tranzaksiyasida (MANDATORY).
 */
@Service
@RequiredArgsConstructor
public class SubscriptionGrants {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    public record Granted(long subscriptionId, String planName, LocalDate validFrom, LocalDate validUntil) {}

    /** Joriy pullik obuna bo'lsa — uning oxiridan keyin davom etadi (kunlar yo'qolmaydi) */
    @Transactional(propagation = Propagation.MANDATORY)
    public Granted grant(FarmerEntity farmer, String planCode, int days, PaymentMethod method, String note) {
        PlanEntity plan = planRepository.findByCode(planCode)
                .filter(p -> !p.isSystemPlan() && p.isActive())
                .orElseThrow(() -> new IllegalStateException("mukofot tarifi topilmadi yoki faol emas: " + planCode));
        LocalDate today = LocalDate.now();
        LocalDate last = subscriptionRepository.lastActiveUntil(farmer.getId(), today);
        LocalDate from = last != null ? last.plusDays(1) : today;
        SubscriptionEntity s = new SubscriptionEntity();
        s.setFarmer(farmer);
        s.setPlan(plan);
        s.setValidFrom(from);
        s.setValidUntil(from.plusDays(days - 1L));
        s.setAmount(BigDecimal.ZERO.setScale(2));
        s.setCurrency(plan.getCurrency());
        s.setPaidAt(LocalDateTime.now());
        s.setPaymentMethod(method);
        s.setNote(note);
        subscriptionRepository.save(s);
        return new Granted(s.getId(), plan.getName(), s.getValidFrom(), s.getValidUntil());
    }
}
