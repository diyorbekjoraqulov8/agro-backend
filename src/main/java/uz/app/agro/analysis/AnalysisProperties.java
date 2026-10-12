package uz.app.agro.analysis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "analysis.worker")
public record AnalysisProperties(
        boolean enabled,

        @Min(value = 1, message = "analysis.worker.batch-size kamida 1")
        @Max(value = 10, message = "analysis.worker.batch-size ko'pi bilan 10 (har biri 30–90 s)")
        int batchSize,

        @Min(value = 1, message = "analysis.worker.max-attempts kamida 1")
        int maxAttempts,

        @NotNull(message = "analysis.worker.stuck-after ko'rsatilishi shart")
        Duration stuckAfter,

        @NotNull(message = "analysis.worker.retry-base ko'rsatilishi shart")
        Duration retryBase
) {}
