package uz.app.agro.advisor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Claude (Anthropic API). Model, limitlar va yoqish — admin Sozlamalarida (Setting.ADVISOR_*); bu yerda — kalit va texnika.
 * api-key bo'sh — AI maslahat o'chiq (bot "hozircha ishlamayapti" deydi). ⚠️ Claude Pro (claude.ai) obunasi API emas.
 */
@Validated
@ConfigurationProperties(prefix = "advisor")
public record AdvisorProperties(
        // Yechilmagan ${ANTHROPIC_API_KEY} literal satr bo'lib kelmasin — shakl tekshiruvi (CLAUDE.md 5.5)
        @Pattern(regexp = "(sk-ant-[A-Za-z0-9_\\-]{20,})?", message = "advisor.api-key: sk-ant-... (ANTHROPIC_API_KEY)")
        String apiKey,

        /** Sinovda mock server; prod — https://api.anthropic.com */
        @NotNull @Pattern(regexp = "https?://[^$]+", message = "advisor.base-url: http(s)://...") String baseUrl,

        @NotNull Duration timeout,
        @Min(1) @Max(10) int maxAttempts
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /** Kalit log'ga tushmasin (record'ning standart toString'i uni ochiq chiqaradi) */
    @Override
    public String toString() {
        return "AdvisorProperties[apiKey=" + (configured() ? "***" : "") + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                + ", maxAttempts=" + maxAttempts + "]";
    }
}
