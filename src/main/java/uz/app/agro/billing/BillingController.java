package uz.app.agro.billing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.billing.dto.*;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Tag(name = "To'lovlar va tariflar", description = "Fermer tariflari, to'lovni qayd etish (admin panel)")
@RestController
@RequiredArgsConstructor
public class BillingController {

    private static final Set<String> SORTABLE = Set.of("validUntil", "paidAt", "createdDate", "id");

    private final BillingService billingService;

    @Operation(summary = "Tariflar", description = "Huquq: `billing:read`. FREE — to'lov yo'q bo'lganda qo'llanadi")
    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return billingService.plans();
    }

    @Operation(summary = "Yangi tarif", description = "Huquq: `billing:manage`")
    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse createPlan(@Valid @RequestBody PlanRequest body) {
        return billingService.createPlan(body);
    }

    @Operation(summary = "Tarif narxi/limitlarini o'zgartirish",
               description = "Huquq: `billing:manage`. Kod o'zgarmaydi. FREE da faqat nom va limitlar")
    @PutMapping("/plans/{id}")
    public PlanResponse updatePlan(@PathVariable Long id, @Valid @RequestBody PlanRequest body) {
        return billingService.updatePlan(id, body);
    }

    @Operation(summary = "Fermerning bugungi tarifi va limitlari", description = "Huquq: `billing:read`")
    @GetMapping("/farmers/{id}/plan")
    public EffectivePlan currentPlan(@PathVariable Long id) {
        return billingService.currentPlan(id);
    }

    @Operation(summary = "Fermer to'lovlari tarixi", description = "Huquq: `billing:read`")
    @GetMapping("/farmers/{id}/subscriptions")
    public List<SubscriptionResponse> history(@PathVariable Long id) {
        return billingService.history(id);
    }

    @Operation(summary = "To'lov qildi — obunani qayd etish",
               description = "Huquq: `billing:manage`. Bo'sh maydonlar tarifdan; muddat joriy obuna oxiridan davom etadi. Audit: `PAYMENT_RECORDED`")
    @PostMapping("/farmers/{id}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse recordPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest body) {
        return billingService.recordPayment(id, body);
    }

    @Operation(summary = "Obunani bekor qilish", description = "Huquq: `billing:manage`. O'chirilmaydi — CANCELLED + sabab")
    @PostMapping("/subscriptions/{id}/cancel")
    public SubscriptionResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest body) {
        return billingService.cancel(id, body);
    }

    @Operation(summary = "Obunalar ro'yxati",
               description = "Huquq: `billing:read`. `expiringBefore=2026-11-01` — shu sanagacha tugaydigan faol obunalar")
    @GetMapping("/subscriptions")
    public PageResponse<SubscriptionResponse> search(
            @RequestParam(required = false) SubscriptionEntity.Status status,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiringBefore,
            @ParameterObject @PageableDefault(size = 50, sort = "validUntil", direction = Sort.Direction.ASC) Pageable pageable) {
        return billingService.search(status, planId, expiringBefore, Pages.guard(pageable, SORTABLE));
    }
}
