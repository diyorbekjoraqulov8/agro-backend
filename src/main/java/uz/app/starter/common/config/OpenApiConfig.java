package uz.app.starter.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String COOKIE_AUTH = "cookieAuth";

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("auth-rbac-starter API")
                        .version("v1")
                        .description("""
                                JWT `AUTH-TOKEN` **HttpOnly cookie**da tashiladi.
                                Tartib: POST /auth/login → GET /auth/me (XSRF-TOKEN olish) → qolgan so'rovlar.
                                O'zgartiruvchi so'rovlar `X-XSRF-TOKEN` header'ini talab qiladi.
                                """))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("AUTH-TOKEN")
                                .description("Login javobida avtomatik o'rnatiladi")));
    }
}