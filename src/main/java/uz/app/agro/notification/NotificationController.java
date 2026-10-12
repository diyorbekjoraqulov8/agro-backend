package uz.app.agro.notification;

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
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;
import uz.app.agro.notification.dto.Audience;
import uz.app.agro.notification.dto.AudiencePreview;
import uz.app.agro.notification.dto.CampaignRequest;
import uz.app.agro.notification.dto.CampaignResponse;
import uz.app.agro.notification.dto.NotificationResponse;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Tag(name = "Bildirishnomalar", description = "Fermerlarga yuborilgan barcha xabarlar (outbox) va admin kampaniyalari")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Set<String> SORTABLE = Set.of("createdDate", "id", "sentAt");
    private static final Set<String> CAMPAIGN_SORTABLE = Set.of("createdDate", "scheduledAt", "id");

    private final NotificationAdminService notifications;
    private final CampaignService campaigns;

    @Operation(summary = "Jurnal", description = "Huquq: `notification:read`")
    @GetMapping
    public PageResponse<NotificationResponse> search(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) Long farmerId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 50, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return notifications.search(status, category, farmerId, campaignId, from, to, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Oxirgi 24 soat statistikasi", description = "Huquq: `notification:read`. Holat → soni")
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return notifications.stats();
    }

    @Operation(summary = "Qayta yuborish", description = "Huquq: `notification:send`. Faqat FAILED/SKIPPED")
    @PostMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable Long id) {
        return notifications.retry(id);
    }

    @Operation(summary = "Kampaniyalar", description = "Huquq: `notification:read`")
    @GetMapping("/campaigns")
    public PageResponse<CampaignResponse> campaigns(
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return campaigns.list(Pages.guard(pageable, CAMPAIGN_SORTABLE));
    }

    @Operation(summary = "Kampaniya", description = "Huquq: `notification:read`")
    @GetMapping("/campaigns/{id}")
    public CampaignResponse campaign(@PathVariable Long id) {
        return campaigns.get(id);
    }

    @Operation(summary = "Auditoriyani hisoblash", description = "Huquq: `notification:send`. Nechta fermerga boradi + misollar")
    @PostMapping("/campaigns/preview")
    public AudiencePreview preview(@Valid @RequestBody Audience audience) {
        return campaigns.preview(audience);
    }

    @Operation(summary = "Yuborish (hozir yoki rejalashtirib)", description = "Huquq: `notification:send`. Audit: `CAMPAIGN_CREATED`")
    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse create(@Valid @RequestBody CampaignRequest body) {
        return campaigns.create(body);
    }

    @Operation(summary = "Bekor qilish", description = "Huquq: `notification:send`. Kutayotgan xabarlar bekor bo'ladi")
    @PostMapping("/campaigns/{id}/cancel")
    public CampaignResponse cancel(@PathVariable Long id) {
        return campaigns.cancel(id);
    }
}
