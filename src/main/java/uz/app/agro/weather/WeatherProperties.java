package uz.app.agro.weather;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Open-Meteo prognozi. Chegaralar (sovuq, issiq…) — admin Sozlamalarida (Setting.WEATHER_*), bu yerda — texnika.
 * api-key bo'sh — bepul API (faqat notijorat; prod'da pullik "customer-api" + kalit).
 */
@Validated
@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(
        @NotNull @Pattern(regexp = "https://[^$]+", message = "weather.forecast-url: https://... (OPEN_METEO_URL)")
        String forecastUrl,

        // Ixtiyoriy: yechilmagan ${OPEN_METEO_API_KEY} literal bo'lib kelmasin — shakl tekshiruvi (CLAUDE.md 5.5)
        @Pattern(regexp = "[A-Za-z0-9_\\-]*", message = "weather.api-key: faqat [A-Za-z0-9_-] (OPEN_METEO_API_KEY)")
        String apiKey,

        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,

        /** Katak prognozi shundan eski bo'lsa — job yangilaydi (modellar 1–6 soatda yangilanadi) */
        @NotNull Duration refreshAfter,

        /** Tunda yuborilmaydi: [quietFrom, quietTo) mahalliy soat — ertalab ketadi */
        @Min(0) @Max(23) int quietFrom,
        @Min(0) @Max(23) int quietTo
) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
