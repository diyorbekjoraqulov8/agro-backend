package uz.app.starter.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.login")
public record LoginProperties(
        @Min(value = 3, message = "app.login.max-attempts kamida 3 bo'lsin")
        @Max(value = 20, message = "app.login.max-attempts ko'pi bilan 20")
        int maxAttempts,

        @NotNull(message = "app.login.lock-duration ko'rsatilishi shart")
        Duration lockDuration
) {}