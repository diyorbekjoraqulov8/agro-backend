package uz.app.agro.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.billing.dto.*;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.CurrentActor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FarmerRepository farmerRepository;
    private final PlanService planService;
    private final BillingMapper mapper;
    private final AuditService auditService;

    // ── Tariflar ───────────────────────────────────────────────────────────

    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public List<PlanResponse> plans() {
        return planRepository.findAllByOrderBySystemPlanDescPriceAsc().stream().map(mapper::toResponse).toList();
    }

    @PreAuthorize(Permissions.CAN_MANAGE_BILLING)
    @Transactional
    public PlanResponse createPlan(PlanRequest req) {
        if (req.code() == null) throw new BadRequestException("code ko'rsatilishi shart");
        if (req.durationDays() == null) throw new BadRequestException("Pullik tarifda durationDays shart");
        if (planRepository.existsByCode(req.code())) throw new ConflictException("Bunday kodli tarif bor: " + req.code());
        PlanEntity p = new PlanEntity();
        p.setCode(req.code());
        apply(p, req);
        planRepository.save(p);
        auditService.event(AuditEvent.PLAN_CREATED).target("PLAN", p.getId()).detail("code", p.getCode()).record();
        return mapper.toResponse(p);
    }

    @PreAuthorize(Permissions.CAN_MANAGE_BILLING)
    @Transactional
    public PlanResponse updatePlan(Long id, PlanRequest req) {
        PlanEntity p = planRepository.findById(id).orElseThrow(() -> new NotFoundException("Tarif", id));
        if (req.code() != null && !req.code().equals(p.getCode())) {
            throw new BadRequestException("Tarif kodi o'zgartirilmaydi");
        }
        if (!p.isSystemPlan() && req.durationDays() == null) {
            throw new BadRequestException("Pullik tarifda durationDays shart");
        }
        var before = mapper.toResponse(p);
        apply(p, req);
        auditService.event(AuditEvent.PLAN_UPDATED).target("PLAN", id)
                .detail("code", p.getCode())
                .detail("before", before.toString())
                .record();
        return mapper.toResponse(p);
    }

    private static void apply(PlanEntity p, PlanRequest r) {
        p.setName(r.name().strip());
        p.setDescription(r.description());
        p.setMaxFieldHa(r.maxFieldHa());
        p.setMaxFields(r.maxFields());
        p.setMaxAnalyses(r.maxAnalyses());
        if (p.isSystemPlan()) {
            // FREE: to'lovsiz holat — narx 0, muddatsiz, o'chirib bo'lmaydi
            p.setActive(true);
            return;
        }
        p.setPrice(r.price());
        p.setCurrency(r.currency());
        p.setDurationDays(r.durationDays());
        p.setActive(r.active());
    }

    // ── To'lovlar ──────────────────────────────────────────────────────────

    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public EffectivePlan currentPlan(Long farmerId) {
        requireFarmer(farmerId);
        return planService.effective(farmerId, LocalDate.now());
    }

    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public List<SubscriptionResponse> history(Long farmerId) {
        requireFarmer(farmerId);
        LocalDate today = LocalDate.now();
        return subscriptionRepository.findByFarmerIdOrderByValidFromDescIdDesc(farmerId).stream()
                .map(s -> mapper.toResponse(s, today)).toList();
    }

    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public PageResponse<SubscriptionResponse> search(SubscriptionEntity.Status status, Long planId,
                                                     LocalDate expiringBefore, Pageable pageable) {
        LocalDate today = LocalDate.now();
        return PageResponse.of(subscriptionRepository
                .findAll(SubscriptionSpecs.filter(status, planId, expiringBefore, today), pageable)
                .map(s -> mapper.toResponse(s, today)));
    }

    /** "To'lov qildi" — admin paneldan. Muddat joriy obuna oxiridan davom etadi (ustma-ust tushmaydi). */
    @PreAuthorize(Permissions.CAN_MANAGE_BILLING)
    @Transactional
    public SubscriptionResponse recordPayment(Long farmerId, PaymentRequest req) {
        FarmerEntity farmer = requireFarmer(farmerId);
        PlanEntity plan = planRepository.findById(req.planId()).orElseThrow(() -> new NotFoundException("Tarif", req.planId()));
        if (plan.isSystemPlan()) throw new BadRequestException("Tizim tarifiga (" + plan.getCode() + ") to'lov qilinmaydi");
        if (!plan.isActive()) throw new BadRequestException("Tarif faol emas: " + plan.getCode());
        // REFERRAL — faqat tizim beradi (taklif mukofoti); xodim qo'lda yozsa statistika buziladi
        if (req.paymentMethod() == PaymentMethod.REFERRAL) throw new BadRequestException("REFERRAL usulini faqat tizim beradi");

        LocalDate today = LocalDate.now();
        LocalDate from = req.validFrom();
        if (from == null) {
            LocalDate last = subscriptionRepository.lastActiveUntil(farmerId, today);
            from = last != null ? last.plusDays(1) : today;
        }
        LocalDate until = req.validUntil() != null ? req.validUntil() : from.plusDays(plan.getDurationDays() - 1L);
        if (until.isBefore(from)) throw new BadRequestException("validUntil validFrom dan oldin bo'lmasin");
        if (until.isAfter(from.plusYears(10))) throw new BadRequestException("Muddat ko'pi bilan 10 yil");

        SubscriptionEntity s = new SubscriptionEntity();
        s.setFarmer(farmer);
        s.setPlan(plan);
        s.setValidFrom(from);
        s.setValidUntil(until);
        s.setAmount((req.amount() != null ? req.amount() : plan.getPrice()).setScale(2, java.math.RoundingMode.HALF_UP));
        s.setCurrency(req.currency() != null ? req.currency() : plan.getCurrency());
        s.setPaidAt(req.paidAt() != null ? req.paidAt() : LocalDateTime.now());
        s.setPaymentMethod(req.paymentMethod());
        s.setNote(req.note());
        CurrentActor.get().ifPresent(a -> {
            s.setRecordedById(a.id());
            s.setRecordedByEmail(a.email());
        });
        subscriptionRepository.save(s);

        auditService.event(AuditEvent.PAYMENT_RECORDED).target("FARMER", farmerId)
                .detail("subscriptionId", s.getId())
                .detail("plan", plan.getCode())
                .detail("amount", s.getAmount() + " " + s.getCurrency())
                .detail("validFrom", from.toString())
                .detail("validUntil", until.toString())
                .record();
        return mapper.toResponse(s, today);
    }

    @PreAuthorize(Permissions.CAN_MANAGE_BILLING)
    @Transactional
    public SubscriptionResponse cancel(Long subscriptionId, CancelRequest req) {
        SubscriptionEntity s = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Obuna", subscriptionId));
        if (s.getStatus() == SubscriptionEntity.Status.CANCELLED) throw new ConflictException("Obuna allaqachon bekor qilingan");
        s.setStatus(SubscriptionEntity.Status.CANCELLED);
        s.setCancelledAt(LocalDateTime.now());
        s.setCancelReason(req.reason().strip());
        auditService.event(AuditEvent.SUBSCRIPTION_CANCELLED).target("FARMER", s.getFarmer().getId())
                .detail("subscriptionId", subscriptionId)
                .detail("reason", s.getCancelReason())
                .record();
        return mapper.toResponse(s, LocalDate.now());
    }

    private FarmerEntity requireFarmer(Long id) {
        return farmerRepository.findById(id).orElseThrow(() -> new NotFoundException("Fermer", id));
    }
}
