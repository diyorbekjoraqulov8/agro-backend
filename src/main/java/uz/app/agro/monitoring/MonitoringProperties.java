package uz.app.agro.monitoring;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Texnika (yuk, oraliqlar). Chegaralar va yoqish — admin Sozlamalarida (Setting.MONITORING_*).
 * batch — bir tick'da nechta dala: har biri Python'da ~2 s (yangi surat yo'q) yoki ~10 s (bor).
 * Sig'im: batch × (hours-to − hours-from) ≈ 720 dala/kun — undan ko'p bo'lsa navbat o'sadi (batch yoki soatlarni oshiring).
 */
@Validated
@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        @Min(1) @Max(500) int batch,
        @NotNull Duration afterNewScene,      // yangi surat ko'rildi — keyingisi kamida 3 kunda (Sentinel-2 qaytishi ~5 kun)
        @NotNull Duration afterNoScene,       // yangi surat yo'q — ertaga
        @NotNull Duration afterUnavailable,   // Python/Earth Search ishlamadi — tezroq qayta
        // Bepul dalalar (monitoring.paid-only yoqiq): faqat kartadagi "Hozirgi holat" uchun — kamroq tez-tez, xabarsiz
        @NotNull Duration freeAfterNewScene,
        @NotNull Duration freeAfterNoScene,
        @Min(0) @Max(23) int hoursFrom,       // server vaqti: xabarlar kunduzi ketsin
        @Min(1) @Max(24) int hoursTo
) {
    @AssertTrue(message = "monitoring.hours-from hours-to dan kichik bo'lsin (aks holda job hech qachon ishlamaydi)")
    public boolean isHoursValid() {
        return hoursFrom < hoursTo;
    }
}
