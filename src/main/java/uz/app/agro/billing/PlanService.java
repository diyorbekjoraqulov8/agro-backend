package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.analysis.AnalysisJobRepository;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.billing.dto.PlanResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Limitlar manbai. @PreAuthorize YO'Q — bot (autentifikatsiyasiz) va xodim service'lari chaqiradi;
 * controller'ga TO'G'RIDAN-TO'G'RI ulanmasin (xodimlar uchun — BillingService).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AnalysisJobRepository jobRepository;
    private final BillingMapper mapper;

    public EffectivePlan effective(long farmerId, LocalDate today) {
        List<SubscriptionEntity> active = subscriptionRepository.findActiveOn(farmerId, today);
        if (!active.isEmpty()) {
            SubscriptionEntity s = active.getFirst();
            PlanEntity p = s.getPlan();
            long used = jobRepository.countPaidJobs(farmerId, s.getValidFrom().atStartOfDay(),
                    s.getValidUntil().plusDays(1).atStartOfDay());
            return new EffectivePlan(p.getCode(), p.getName(), true, s.getId(), s.getValidUntil(),
                    p.getMaxFieldHa(), p.getMaxTotalHa(), p.getMaxFields(), p.getMaxAnalyses(), used);
        }
        PlanEntity free = freePlan();
        return new EffectivePlan(free.getCode(), free.getName(), false, null, null,
                free.getMaxFieldHa(), free.getMaxTotalHa(), free.getMaxFields(), free.getMaxAnalyses(), jobRepository.countFreeJobs(farmerId));
    }

    /** Bot "💎 Tariflar": faqat faollar — avval tizim (Bepul), keyin narx bo'yicha, "narx kelishiladi" (Agro Biznes) — oxirida */
    public List<PlanResponse> catalog() {
        return planRepository.findAllByOrderBySystemPlanDescContactSalesAscPriceAsc().stream()
                .filter(PlanEntity::isActive).map(mapper::toResponse).toList();
    }

    /**
     * Bugun faol pullik obunasi bormi — limitlarsiz, bitta so'rov (monitoring kartochkasi).
     * ⚠️ Xuddi shu ta'rif SQL'da: FieldMonitoringRepository.ELIGIBLE — o'zgarsa ikkalasi ham
     */
    public boolean paidOn(long farmerId, LocalDate day) {
        return !subscriptionRepository.findActiveOn(farmerId, day).isEmpty();
    }

    PlanEntity freePlan() {
        // V12 seed qiladi; yo'q bo'lsa — konfiguratsiya xatosi, jim davom etish xavfli
        return planRepository.findByCode(PlanEntity.FREE)
                .orElseThrow(() -> new IllegalStateException("plans jadvalida FREE rejasi yo'q (V12)"));
    }
}
