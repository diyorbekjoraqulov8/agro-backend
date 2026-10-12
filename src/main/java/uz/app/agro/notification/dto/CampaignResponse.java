package uz.app.agro.notification.dto;

import uz.app.agro.notification.CampaignStatus;

import java.time.LocalDateTime;
import java.util.Map;

/** counts — xabarlar holati bo'yicha (PENDING, SENT, FAILED…) */
public record CampaignResponse(
        Long id, String title, Map<String, String> texts, Map<String, String> buttonTexts, String buttonUrl,
        Map<String, Object> audience, CampaignStatus status, LocalDateTime scheduledAt, Integer total, Map<String, Long> counts,
        String createdByEmail, LocalDateTime createdDate, LocalDateTime startedAt, LocalDateTime finishedAt
) {}
