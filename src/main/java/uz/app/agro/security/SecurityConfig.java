package uz.app.agro.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(-1)
    public SecurityFilterChain managementChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .anyRequest().denyAll()
                )
                .headers(SecurityConfig::securityHeaders);
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(SecurityConfig::securityHeaders);
        return http.build();
    }

    /**
     * Telegram Mini App → server. Auth — initData imzosi (controller'da tekshiriladi), cookie/JWT YO'Q →
     * CSRF kerak emas va oauth2ResourceServer qo'yilmaydi. CORS — faqat Mini App domeni (ADR #25 istisnosi:
     * Mini App Vercel'da, boshqa origin; cookie yuborilmaydi, allowCredentials=false).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain miniAppChain(HttpSecurity http,
                                            @Value("${telegram.miniapp-url:}") String miniappUrl) throws Exception {
        http
                .securityMatcher("/api/v1/miniapp/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(miniAppCors(miniappUrl)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(SecurityConfig::securityHeaders);
        return http.build();
    }

    private static CorsConfigurationSource miniAppCors(String miniappUrl) {
        CorsConfiguration c = new CorsConfiguration();
        String origin = origin(miniappUrl);
        if (origin != null) c.setAllowedOrigins(List.of(origin));
        c.setAllowedMethods(List.of("POST"));
        c.setAllowedHeaders(List.of("Content-Type"));
        c.setAllowCredentials(false);
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }

    /** "https://agro.vercel.app/path?x" → "https://agro.vercel.app"; bo'sh/noto'g'ri — null (CORS yopiq) */
    static String origin(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI u = URI.create(url.strip());
            if (u.getScheme() == null || u.getHost() == null) return null;
            return u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiChain(
            HttpSecurity http,
            DbAuthenticationConverter authConverter
    ) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(plainCsrfHandler())
                )
                .addFilterAfter(new CookieCsrfEnforcementFilter(), CsrfFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authConverter))
                )
                .headers(SecurityConfig::securityHeaders);

        return http.build();
    }

    @Bean
    @Order(0)
    public SecurityFilterChain docsChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "font-src 'self' data:; " +
                                        "connect-src 'self'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    private static CsrfTokenRequestAttributeHandler plainCsrfHandler() {
        CsrfTokenRequestAttributeHandler h = new CsrfTokenRequestAttributeHandler();
        h.setCsrfRequestAttributeName(null);
        return h;
    }

    private static void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(ref -> ref
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicyHeader(pp -> pp
                        .policy("camera=(), microphone=(), geolocation=()"))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000));
    }
}
