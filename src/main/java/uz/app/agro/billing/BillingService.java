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
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.CurrentActor;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final NotificationService notifications;
    private final SettingsService settings;
    private final DiscountPricing pricing;

    // ── Tariflar ───────────────────────────────────────────────────────────

    @PreAuthorize(Permissions.CAN_READ_BILLING)
    public List<PlanResponse> plans() {
        return planRepository.findAllByOrderBySystemPlanDescContactSalesAscPriceAsc().stream().map(mapper::toResponse).toList();
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
        p.setMaxTotalHa(r.maxTotalHa());
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
        p.setContactSales(r.contactSales());
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
        if (farmer.isLab()) throw new ConflictException("Lab fermerga to'lov yozilmaydi");
        PlanEntity plan = planRepository.findById(req.planId()).orElseThrow(() -> new NotFoundException("Tarif", req.planId()));
        if (plan.isSystemPlan()) throw new BadRequestException("Tizim tarifiga (" + plan.getCode() + ") to'lov qilinmaydi");
        if (!plan.isActive()) throw new BadRequestException("Tarif faol emas: " + plan.getCode());
        // REFERRAL / TRIAL — faqat tizim beradi; xodim qo'lda yozsa statistika buziladi
        if (req.paymentMethod().systemOnly()) throw new BadRequestException(req.paymentMethod() + " usulini faqat tizim beradi");
        // Agro Biznes: narx kelishiladi — rejadagi 0 yozilib qolmasin
        if (plan.isContactSales() && (req.amount() == null || req.amount().signum() <= 0)) {
            throw new BadRequestException("Bu tarif narxi kelishiladi — kelishilgan summani kiriting");
        }

        LocalDate today = LocalDate.now();
        LocalDate last = subscriptionRepository.lastActiveUntil(farmerId, today);
        LocalDate from = req.validFrom();
        if (from == null) from = last != null ? last.plusDays(1) : today;
        // Muddat qo'lda berilsa — summa uchun davrlar soni o'shandan (masalan 90 kun = 3 × 30); aks holda periods (bo'sh — 1)
        int periods = req.periods() != null ? req.periods()
                : req.validUntil() != null ? (int) Math.max(1, Math.round(
                        (ChronoUnit.DAYS.between(from, req.validUntil()) + 1) / (double) plan.getDurationDays())) : 1;
        LocalDate until = req.validUntil() != null ? req.validUntil() : from.plusDays((long) plan.getDurationDays() * periods - 1L);
        if (until.isBefore(from)) throw new BadRequestException("validUntil validFrom dan oldin bo'lmasin");
        // Allaqachon tugagan obuna — deyarli har doim xato (Swagger validUntil'ga bugungi sanani qo'yadi va h.k.)
        if (until.isBefore(today)) throw new BadRequestException("Tugash sanasi o'tib ketgan: " + until);
        if (until.isAfter(from.plusYears(10))) throw new BadRequestException("Muddat ko'pi bilan 10 yil");

        SubscriptionEntity s = new SubscriptionEntity();
        s.setFarmer(farmer);
        s.setPlan(plan);
        s.setValidFrom(from);
        s.setValidUntil(until);
        // Summa bo'sh — fermerga mos eng foydali chegirma bilan (narx × davr); auditoriya obuna yozilishidan OLDIN aniqlanadi
        PlanPrice price = pricing.price(farmerId, mapper.toResponse(plan), today);
        BigDecimal amount = req.amount();
        Long discountId;
        if (amount == null) {
            amount = price.finalPrice().multiply(BigDecimal.valueOf(periods));
            discountId = price.discountId();
        } else {
            // Qo'lda summa: chegirma faqat shu fermerga hozir haqiqatan qo'llanadigani bo'lsa yoziladi (statistika shishmasin)
            discountId = req.discountId() != null && req.discountId().equals(price.discountId()) ? req.discountId() : null;
        }
        s.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        s.setDiscountId(discountId);
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
                .detail("discountId", discountId)
                .record();
        notifyActivated(farmerId, plan, until, last != null);
        return mapper.toResponse(s, today);
    }

    /**
     * Fermerga "✅ Pro faollashdi" (outbox — shu tranzaksiyada, rollback bo'lsa xabar ham yo'q). Faol obunasi bor
     * edi — "uzaytirildi". Taklif dasturi yoqilgan bo'lsa — keyingi oyni bepul olish yo'li.
     */
    private void notifyActivated(long farmerId, PlanEntity plan, LocalDate until, boolean extended) {
        boolean friends = settings.getBool(Setting.REFERRAL_ENABLED);
        String key = (extended ? "plan.extended" : "plan.activated") + (friends ? ".friends" : "");
        notifications.enqueueMessage(farmerId, NotificationCategory.SYSTEM, key, plan.getName(), until,
                String.valueOf(settings.getInt(Setting.REFERRAL_REQUIRED)),
                String.valueOf(settings.getInt(Setting.REFERRAL_REWARD_DAYS)));
    }

    @PreAuthorize(Permissions.CAN_MANAGE_BILLING)
    @Transactional
    public SubscriptionResponse cancel(Long subscriptionId, CancelRequest req) {
        SubscriptionEntity s = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Obuna", subscriptionId));
        // Lab fermer tizim obunasisiz FREE ga tushadi — Lab dalalari monitoringdan jimgina chiqib qoladi
        if (s.getFarmer().isLab()) throw new ConflictException("Lab dalalarining tizim obunasini bekor qilib bo'lmaydi");
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
