package uz.app.starter.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CookieService {

    private final CookieProperties props;

    public static final String AUTH_COOKIE = "AUTH-TOKEN";
    public static final String REFRESH_COOKIE = "REFRESH-TOKEN";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    public ResponseCookie refresh(String token, Duration ttl) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path(REFRESH_PATH)
                .maxAge(ttl)
                .build();
    }

    public ResponseCookie clearRefresh() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path(REFRESH_PATH)
                .maxAge(0)
                .build();
    }

    public ResponseCookie auth(String token, Duration ttl) {
        return ResponseCookie.from(AUTH_COOKIE, token)
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path("/")
                .maxAge(ttl)
                .build();
    }

    public ResponseCookie clearAuth() {
        return ResponseCookie.from(AUTH_COOKIE, "")
                .httpOnly(true)
                .secure(props.secure())
                .sameSite(props.sameSite())
                .path("/")
                .maxAge(0)
                .build();
    }
}
