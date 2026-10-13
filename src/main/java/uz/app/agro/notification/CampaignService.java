package uz.app.agro.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.notification.dto.Audience;
import uz.app.agro.notification.dto.AudiencePreview;
import uz.app.agro.notification.dto.CampaignRequest;
import uz.app.agro.notification.dto.CampaignResponse;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.CurrentActor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Admin → Bildirishnomalar: hammaga / filtr / bitta fermerga xabar. Tarqatish — CampaignJob (fonda, navbat orqali) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

    private static final Set<String> LANGS = Set.of("uz", "ru", "en");
    private static final int MAX_TEXT = 3500;        // Telegram ≤ 4096
    private static final int MAX_BUTTON = 40;

    private final CampaignRepository campaigns;
    private final NotificationRepository notifications;
    private final AudienceQuery audienceQuery;
    private final AuditService auditService;
    private final FarmerRepository farmers;

    @PreAuthorize(Permissions.CAN_SEND_NOTIFICATION)
    public AudiencePreview preview(Audience a) {
        validate(a);
        Map<String, Long> byLang = audienceQuery.countByLanguage(a);
        return new AudiencePreview(byLang.values().stream().mapToLong(Long::longValue).sum(), byLang,
                audienceQuery.targets(a, 5).stream().map(AudienceQuery.Target::name).toList());
    }

    @PreAuthorize(Permissions.CAN_SEND_NOTIFICATION)
    @Transactional
    public CampaignResponse create(CampaignRequest req) {
        validate(req.audience());
        Map<String, String> texts = texts(req.texts(), MAX_TEXT, "Xabar");
        Map<String, String> buttons = req.buttonUrl() == null ? null : texts(req.buttonTexts(), MAX_BUTTON, "Tugma matni");
        if (req.buttonUrl() == null && req.buttonTexts() != null && !req.buttonTexts().isEmpty()) {
            throw new BadRequestException("Tugma uchun havola (https://…) kerak");
        }
        if (req.buttonUrl() != null) guardPublicUrl(req.buttonUrl());
        LocalDateTime when = req.scheduledAt() == null ? LocalDateTime.now()
                : req.scheduledAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        if (when.isBefore(LocalDateTime.now().minusMinutes(1))) throw new BadRequestException("Vaqt o'tib ketgan");
        CampaignEntity c = new CampaignEntity();
        c.setTitle(req.title().strip());
        c.setTexts(texts);
        c.setButtonTexts(buttons);
        c.setButtonUrl(req.buttonUrl());
        @SuppressWarnings("unchecked")
        Map<String, Object> audience = JsonMapper.shared().convertValue(req.audience(), Map.class);
        c.setAudience(audience);
        c.setScheduledAt(when);
        c.setCreatedByEmail(CurrentActor.get().map(x -> x.email()).orElse(null));
        campaigns.save(c);
        auditService.event(AuditEvent.CAMPAIGN_CREATED).target("CAMPAIGN", c.getId())
                .detail("title", c.getTitle()).detail("audience", req.audience().type().name())
                .detail("scheduledAt", when.toString()).record();
        return toResponse(c);
    }

    @PreAuthorize(Permissions.CAN_READ_NOTIFICATION)
    public PageResponse<CampaignResponse> list(Pageable pageable) {
        Page<CampaignEntity> page = campaigns.findAll(pageable);
        Map<Long, Map<String, Long>> counts = counts(page.map(CampaignEntity::getId).toList());
        return PageResponse.of(page.map(c -> toResponse(c, counts.getOrDefault(c.getId(), Map.of()))));
    }

    @PreAuthorize(Permissions.CAN_READ_NOTIFICATION)
    public CampaignResponse get(Long id) {
        return toResponse(require(id));
    }

    /** Rejalashtirilgan yoki yuborilayotgan — kutayotgan xabarlar bekor (yuborilganlari qaytmaydi) */
    @PreAuthorize(Permissions.CAN_SEND_NOTIFICATION)
    @Transactional
    public CampaignResponse cancel(Long id) {
        CampaignEntity c = require(id);
        if (c.getStatus() == CampaignStatus.DONE || c.getStatus() == CampaignStatus.CANCELLED) {
            throw new ConflictException("Kampaniya allaqachon " + (c.getStatus() == CampaignStatus.DONE ? "yakunlangan" : "bekor qilingan"));
        }
        c.setStatus(CampaignStatus.CANCELLED);
        c.setFinishedAt(LocalDateTime.now());
        int cancelled = notifications.cancelCampaign(id);
        auditService.event(AuditEvent.CAMPAIGN_CANCELLED).target("CAMPAIGN", id).detail("cancelled", cancelled).record();
        return toResponse(campaigns.findById(id).orElseThrow());
    }

    // ── Tekshiruv ──

    private void validate(Audience a) {
        if (a.type() == Audience.Type.SINGLE) {
            if (a.farmerId() == null) throw new BadRequestException("Fermer tanlanmagan");
            if (!farmers.existsById(a.farmerId())) throw new NotFoundException("Fermer", a.farmerId());
        }
        if (a.languages() != null && !LANGS.containsAll(a.languages())) throw new BadRequestException("Til: uz, ru, en");
        if (a.countries() != null && a.countries().stream().anyMatch(c -> c == null || !c.matches("[A-Z]{2}"))) {
            throw new BadRequestException("Mamlakat kodi: 2 ta katta harf (UZ, KZ…)");
        }
        if (a.registeredFrom() != null && a.registeredTo() != null && a.registeredTo().isBefore(a.registeredFrom())) {
            throw new BadRequestException("Sana oralig'i noto'g'ri");
        }
    }

    /**
     * Telegram tugmada faqat ommaviy domenni qabul qiladi: localhost, IP yoki nuqtasiz host bo'lsa butun xabarni
     * rad etadi (400) — kampaniya hech kimga yetmaydi. Yaratishdayoq aytamiz.
     */
    static void guardPublicUrl(String url) {
        String host;
        try {
            host = java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tugma havolasi noto'g'ri: " + url);
        }
        if (host == null || !host.contains(".") || host.equalsIgnoreCase("localhost") || host.endsWith(".localhost")
                || host.matches("[0-9.]+") || host.startsWith("[")) {
            throw new BadRequestException("Tugma havolasi ommaviy sayt bo'lishi kerak (masalan https://dropy.uz/...). "
                    + "localhost va IP manzilni Telegram qabul qilmaydi");
        }
    }

    private static Map<String, String> texts(Map<String, String> in, int max, String what) {
        if (in == null) throw new BadRequestException(what + ": o'zbekcha matn majburiy");
        Map<String, String> out = new HashMap<>();
        in.forEach((k, v) -> {
            if (!LANGS.contains(k)) throw new BadRequestException("Noma'lum til: " + k);
            String s = v == null ? "" : v.strip();
            if (s.length() > max) throw new BadRequestException(what + " (" + k + "): ko'pi bilan " + max + " belgi");
            if (!s.isEmpty()) out.put(k, s);
        });
        if (!out.containsKey("uz")) throw new BadRequestException(what + ": o'zbekcha matn majburiy");
        return out;
    }

    private CampaignEntity require(Long id) {
        return campaigns.findById(id).orElseThrow(() -> new NotFoundException("Kampaniya", id));
    }

    private Map<Long, Map<String, Long>> counts(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return notifications.campaignCounts(ids).stream().collect(Collectors.groupingBy(
                NotificationRepository.CampaignStatusCount::getCampaignId,
                Collectors.toMap(NotificationRepository.StatusCount::getStatus, NotificationRepository.StatusCount::getCount)));
    }

    private CampaignResponse toResponse(CampaignEntity c) {
        return toResponse(c, counts(List.of(c.getId())).getOrDefault(c.getId(), Map.of()));
    }

    private static CampaignResponse toResponse(CampaignEntity c, Map<String, Long> counts) {
        return new CampaignResponse(c.getId(), c.getTitle(), c.getTexts(), c.getButtonTexts(), c.getButtonUrl(), c.getAudience(),
                c.getStatus(), c.getScheduledAt(), c.getTotal(), counts, c.getCreatedByEmail(), c.getCreatedDate(),
                c.getStartedAt(), c.getFinishedAt());
    }
}
