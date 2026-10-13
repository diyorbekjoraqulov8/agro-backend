package uz.app.agro.monitoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Texnika (yuk, oraliqlar). Chegaralar va yoqish — admin Sozlamalarida (Setting.MONITORING_*).
 * batch — bir tick'da nechta dala: har biri Python'da ~2 s (yangi surat yo'q) yoki ~10 s (bor).
 */
@Validated
@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        @Min(1) @Max(500) int batch,
        @NotNull Duration afterNewScene,      // yangi surat ko'rildi — keyingisi kamida 3 kunda (Sentinel-2 qaytishi ~5 kun)
        @NotNull Duration afterNoScene,       // yangi surat yo'q — ertaga
        @NotNull Duration afterUnavailable,   // Python/Earth Search ishlamadi — tezroq qayta
        @Min(0) @Max(23) int hoursFrom,       // server vaqti: xabarlar kunduzi ketsin
        @Min(1) @Max(24) int hoursTo
) {}
