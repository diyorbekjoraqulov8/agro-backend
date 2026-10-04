package uz.app.starter.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
        boolean secure,

        @NotBlank(message = "app.cookie.same-site ko'rsatilishi shart")
        @Pattern(regexp = "Strict|Lax|None", message = "same-site: Strict, Lax yoki None")
        String sameSite
) {}