package uz.app.agro.satellite;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "satellite")
public record SatelliteProperties(
        @NotNull(message = "satellite.base-url ko'rsatilishi shart")
        @Pattern(regexp = "https?://[^$]+", message = "satellite.base-url: http(s)://... (SATELLITE_URL)")
        String baseUrl,

        // @NotBlank EMAS: yechilmagan ${SATELLITE_INTERNAL_KEY} literal satr bo'lib keladi (CLAUDE.md 5.5)
        @NotNull(message = "satellite.internal-key ko'rsatilishi shart")
        @Pattern(regexp = "[A-Za-z0-9_\\-]{16,}", message = "satellite.internal-key: kamida 16 belgi [A-Za-z0-9_-] (SATELLITE_INTERNAL_KEY)")
        String internalKey,

        @NotNull(message = "satellite.connect-timeout ko'rsatilishi shart")
        Duration connectTimeout,

        @NotNull(message = "satellite.read-timeout ko'rsatilishi shart")
        Duration readTimeout
) {}
