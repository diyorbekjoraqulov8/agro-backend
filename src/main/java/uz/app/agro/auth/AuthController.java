package uz.app.agro.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.auth.dto.LoginRequest;
import uz.app.agro.auth.dto.MeResponse;
import uz.app.agro.auth.dto.RegisterRequest;
import uz.app.agro.auth.dto.SessionResponse;
import uz.app.agro.security.AuthUser;
import uz.app.agro.security.CookieService;
import uz.app.agro.security.JwtService;
import uz.app.agro.security.SessionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final SessionService sessionService;
    private final AuthService authService;
    private final CookieService cookieService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        this.authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        var result = this.authService.login(request, httpRequest);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieService.auth(
                        result.accessToken(),
                        result.accessTtl()
                ).toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieService.refresh(
                        result.refreshToken(),
                        result.refreshTtl()
                ).toString()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = "REFRESH-TOKEN", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        var result = authService.refresh(refreshToken);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieService.auth(
                        result.accessToken(),
                        result.accessTtl()
                ).toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieService.refresh(
                        result.refreshToken(),
                        result.refreshTtl()
                ).toString()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthUser user,
            HttpServletResponse response
    ) {
        this.authService.logout(user.sessionId());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieService.clearAuth().toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieService.clearRefresh().toString()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return new MeResponse(user.id(), user.email(), user.roles(), user.permissions());
    }

    /** Support guruhida (support bot) javob shu xodim nomidan yoziladi */
    @GetMapping("/me/telegram")
    public Map<String, Long> myTelegram(@AuthenticationPrincipal AuthUser user) {
        Map<String, Long> m = new HashMap<>();              // Map.of null qabul qilmaydi (CLAUDE.md 5.6)
        m.put("telegramUserId", authService.myTelegram(user));
        return m;
    }

    /** Bog'lash — faqat support bot orqali (POST /support/telegram-link → havola); bu yerda — uzish */
    @DeleteMapping("/me/telegram")
    public ResponseEntity<Void> unlinkTelegram(@AuthenticationPrincipal AuthUser user) {
        authService.unlinkTelegram(user);
        return ResponseEntity.noContent().build();
    }

//    Sessions
    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal AuthUser user) {
        return authService.mySessions(user);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable UUID id) {
        authService.revokeSession(user, id);
        return ResponseEntity.noContent().build();
    }
}
