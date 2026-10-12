package uz.app.agro.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

/** texts.uz majburiy (boshqa til bo'sh bo'lsa — uz); tugma ixtiyoriy (matn + https url); scheduledAt null — darhol */
public record CampaignRequest(
        @NotBlank @Size(max = 120) String title,
        @NotNull Map<String, String> texts,
        Map<String, String> buttonTexts,
        @Pattern(regexp = "https://\\S{3,490}", message = "Tugma havolasi https:// bilan boshlansin") String buttonUrl,
        @NotNull @Valid Audience audience,
        LocalDateTime scheduledAt
) {}
