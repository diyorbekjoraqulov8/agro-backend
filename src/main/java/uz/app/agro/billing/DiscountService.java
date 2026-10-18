package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.billing.dto.DiscountRequest;
import uz.app.agro.billing.dto.DiscountResponse;
import uz.app.agro.billing.dto.PlanPrice;
import uz.app.agro.billing.dto.SeasonOffer;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.CurrentActor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Admin: chegirmalar (Moliya → Chegirmalar). O'chirish yo'q — active=false (tarix discount_id bilan bog'langan) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscountService {

    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(90);

    private final DiscountRepository repository;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final FarmerRepository farmers;
    private final PlanService planService;
    private final DiscountPricing pricing;
    private final SeasonOffers seasons;
    private final AuditService auditService;

    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public List<DiscountResponse> list() {
        LocalDate today = LocalDate.now();
        Map<Long, Long> used = new HashMap<>();
        for (var u : subscriptions.discountUses()) used.put(u.getDiscountId(), u.getUsed());
        return repository.findAllByOrderByActiveDescStartsOnDescIdDesc().stream()
                .map(d -> toResponse(d, today, used.getOrDefault(d.getId(), 0L))).toList();
    }

    @PreAuthorize(Permissions.CAN_MANAGE_BILLING)
    @Transactional
    public DiscountResponse create(DiscountRequest req) {
        DiscountEntity d = new DiscountEntity();
        apply(d, req);
        repository.save(d);
        auditService.event(AuditEvent.DISCOUNT_CREATED).target("DISCOUNT", d.getId())
                .detail("name", d.getName()).detail("value", d.getValue() + " " + d.getValueType()).record();
        return toResponse(d, LocalDate.now(), 0);
    }

    @PreAuthorize(Permissions.CAN_MANAGE_BILLING)
    @Transactional
    public DiscountResponse update(Long id, DiscountRequest req) {
        DiscountEntity d = repository.findById(id).orElseThrow(() -> new NotFoundException("Chegirma", id));
        String before = d.getName() + " " + d.getValue() + " " + d.getValueType() + " " + d.getAudiences() + " ["
                + d.getPlanIds() + "] " + d.getStartsOn() + ".." + d.getEndsOn() + " active=" + d.isActive();
        apply(d, req);
        auditService.event(AuditEvent.DISCOUNT_UPDATED).target("DISCOUNT", id).detail("before", before).record();
        return toResponse(d, LocalDate.now(), subscriptions.discountUses().stream()
                .filter(u -> id.equals(u.getDiscountId())).mapToLong(SubscriptionRepository.DiscountUse::getUsed).sum());
    }

    /** Admin "To'lov qildi" oynasi: shu fermerga har tarif narxi (chegirma bilan) */
    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public List<PlanPrice> pricesFor(Long farmerId) {
        if (!farmers.existsById(farmerId)) throw new NotFoundException("Fermer", farmerId);
        var catalog = planService.catalog().stream().filter(p -> !p.systemPlan()).toList();
        return List.copyOf(pricing.prices(farmerId, catalog, LocalDate.now()).values());
    }

    /** Admin "To'lov qildi": shu fermerga mavsumiy taklif (sozlama o'chiq / sotuv davri emas — bo'sh) */
    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public Optional<SeasonOffer> seasonOfferFor(Long farmerId) {
        if (!farmers.existsById(farmerId)) throw new NotFoundException("Fermer", farmerId);
        return seasons.offer(farmerId, LocalDate.now());
    }

    private void apply(DiscountEntity d, DiscountRequest req) {
        if (req.valueType() == DiscountEntity.ValueType.PERCENT && req.value().compareTo(MAX_PERCENT) > 0) {
            throw new BadRequestException("Foiz ko'pi bilan 90");
        }
        if (req.endsOn() != null && req.endsOn().isBefore(req.startsOn())) {
            throw new BadRequestException("Tugash sanasi boshlanishdan oldin bo'lmasin");
        }
        Set<Long> planIds = req.planIds() == null ? Set.of() : new HashSet<>(req.planIds());
        List<PlanEntity> chosen = plans.findAllById(planIds);
        if (chosen.size() != planIds.size()) throw new BadRequestException("Tarif topilmadi");
        for (PlanEntity p : chosen) {
            if (p.isSystemPlan() || p.isContactSales()) {
                throw new BadRequestException("Chegirma faqat narxli pullik tarifga: " + p.getName());
            }
        }
        // Qat'iy summa — so'mda va narxdan kichik: aks holda 0 so'mlik "pullik" obuna yoki boshqa valyutaga noto'g'ri ayirma
        if (req.valueType() == DiscountEntity.ValueType.FIXED) {
            List<PlanEntity> targets = planIds.isEmpty() ? plans.findAll().stream()
                    .filter(p -> p.isActive() && !p.isSystemPlan() && !p.isContactSales() && p.getPrice().signum() > 0).toList() : chosen;
            for (PlanEntity p : chosen) {
                if (!"UZS".equals(p.getCurrency())) {
                    throw new BadRequestException("Qatʼiy summa faqat soʻmdagi tarifga: «" + p.getName() + "» — foiz tanlang");
                }
            }
            for (PlanEntity p : targets) {
                if ("UZS".equals(p.getCurrency()) && req.value().compareTo(p.getPrice()) >= 0) {
                    throw new BadRequestException("Chegirma summasi «" + p.getName() + "» narxidan kichik boʻlsin");
                }
            }
        }
        d.setName(req.name().strip());
        d.setKind(req.kind());
        d.setValueType(req.valueType());
        d.setValue(req.value());
        d.audienceSet(req.audiences());
        d.planIdSet(planIds);
        d.setStartsOn(req.startsOn());
        d.setEndsOn(req.endsOn());
        d.setActive(req.active());
        d.setUpdatedByEmail(CurrentActor.get().map(a -> a.email()).orElse(null));
    }

    private static DiscountResponse toResponse(DiscountEntity d, LocalDate today, long used) {
        return new DiscountResponse(d.getId(), d.getName(), d.getKind(), d.getValueType(), d.getValue(), d.audienceSet(),
                d.planIdSet(), d.getStartsOn(), d.getEndsOn(), d.isActive(), d.activeOn(today),
                used, d.getUpdatedDate(), d.getUpdatedByEmail());
    }
}
