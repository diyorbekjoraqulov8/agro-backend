package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.analysis.AnalysisJobRepository;
import uz.app.agro.billing.dto.EffectivePlan;

import java.math.BigDecimal;
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

    public EffectivePlan effective(long farmerId, LocalDate today) {
        List<SubscriptionEntity> active = subscriptionRepository.findActiveOn(farmerId, today);
        if (!active.isEmpty()) {
            SubscriptionEntity s = active.getFirst();
            PlanEntity p = s.getPlan();
            long used = jobRepository.countPaidJobs(farmerId, s.getValidFrom().atStartOfDay(),
                    s.getValidUntil().plusDays(1).atStartOfDay());
            return new EffectivePlan(p.getCode(), p.getName(), true, s.getId(), s.getValidUntil(),
                    p.getMaxFieldHa(), p.getMaxFields(), p.getMaxAnalyses(), used);
        }
        PlanEntity free = freePlan();
        return new EffectivePlan(free.getCode(), free.getName(), false, null, null,
                free.getMaxFieldHa(), free.getMaxFields(), free.getMaxAnalyses(), jobRepository.countFreeJobs(farmerId));
    }

    /** "Pullik tarifda N ga gacha" — faol pullik rejalar orasida eng kattasi */
    public BigDecimal maxPaidFieldHa() {
        return planRepository.maxPaidFieldHa().orElse(null);
    }

    PlanEntity freePlan() {
        // V12 seed qiladi; yo'q bo'lsa — konfiguratsiya xatosi, jim davom etish xavfli
        return planRepository.findByCode(PlanEntity.FREE)
                .orElseThrow(() -> new IllegalStateException("plans jadvalida FREE rejasi yo'q (V12)"));
    }
}
