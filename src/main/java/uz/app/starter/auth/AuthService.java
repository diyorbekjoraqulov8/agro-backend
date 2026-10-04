package uz.app.starter.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.starter.audit.AuditEvent;
import uz.app.starter.audit.AuditService;
import uz.app.starter.audit.AuditLog;
import uz.app.starter.auth.dto.LoginRequest;
import uz.app.starter.auth.dto.RegisterRequest;
import uz.app.starter.auth.dto.SessionResponse;
import uz.app.starter.common.exception.ConflictException;
import uz.app.starter.rbac.RoleNames;
import uz.app.starter.rbac.RoleRepository;
import uz.app.starter.rbac.Role;
import uz.app.starter.security.*;
import uz.app.starter.security.Session;
import uz.app.starter.user.UserRepository;
import uz.app.starter.user.UserEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    private final SessionService sessionService;
    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SessionRepository sessionRepository;
    private final AuditService auditService;

    public record LoginResult(
            String accessToken,
            Duration accessTtl,
            String refreshToken,
            Duration refreshTtl
    ) {}

    @Value("${jwt.refresh-token-ttl}")
    private Duration refreshTtl;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Bu email allaqachon ro'yxatdan o'tgan");
        }

        Role userRole = roleRepository.findByName(RoleNames.USER)
                .orElseThrow(() -> new IllegalStateException(
                        "USER roli topilmadi — seed migration buzuq"));

        UserEntity user = new UserEntity();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));

        auditService.event(AuditEvent.USER_REGISTERED)
                .actor(user.getId(), user.getEmail())
                .target("USER", user.getId())
                .record();

        user.getRoles().add(userRole);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Bu email allaqachon ro'yxatdan o'tgan");
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();

        String refreshToken = refreshTokenService.generate();

        Session session = sessionService.create(
                user.id(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRemoteAddr(),
                refreshTtl,
                refreshTokenService.hash(refreshToken)
        );

        String accessToken = jwtService.generate(user.id(), user.email(), session.getId());

        return new LoginResult(
                accessToken,
                jwtService.accessTokenTtl(),
                refreshToken,
                refreshTtl
        );
    }

    @Transactional
    public LoginResult refresh(String refreshToken) {
        if (refreshToken == null) throw new BadCredentialsException("Refresh token yo'q");

        String hash = refreshTokenService.hash(refreshToken);

        sessionRepository.findByPreviousRefreshTokenHash(hash).ifPresent(stolen -> {
            sessionService.revokeAllForUser(stolen.getUserId());

            auditService.event(AuditEvent.REFRESH_TOKEN_REUSE)
                    .failure()
                    .actor(stolen.getUserId(), null)
                    .target("SESSION", stolen.getId())
                    .detail("reason", "previous refresh token reused")
                    .recordIndependently();

            throw new BadCredentialsException("Refresh token qayta ishlatildi");
        });

        Session session = sessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token yaroqsiz"));

        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BadCredentialsException("Sessiya yaroqsiz");

        UserEntity user = userRepository.findById(session.getUserId())
            .orElseThrow(() -> new BadCredentialsException("Foydalanuvchi topilmadi"));

        if (!user.isActive()) throw new BadCredentialsException("Foydalanuvchi faol emas");

        String newRefreshToken = refreshTokenService.generate();
        sessionService.rotate(session, refreshTokenService.hash(newRefreshToken));

        String accessToken = jwtService.generate(user.getId(), user.getEmail(), session.getId());
        return new LoginResult(
                accessToken,
                jwtService.accessTokenTtl(),
                newRefreshToken,
                refreshTtl
        );
    }

    @Transactional
    public void logout(UUID sessionId) {
        auditService.event(AuditEvent.LOGOUT)
                .target("SESSION", sessionId)
                .record();
        sessionService.revoke(sessionId);
    }

    public List<SessionResponse> mySessions(AuthUser user) {
        return sessionService.activeSessions(user.id()).stream()
                .map(
                    s -> new SessionResponse(
                        s.getId(),
                        s.getDevice(),
                        s.getIp(),
                        s.getCreatedDate(),
                        s.getLastSeenAt(),
                        s.getId().equals(user.sessionId()))
                )
                .toList();
    }

    @Transactional
    public void revokeSession(AuthUser user, UUID sessionId) {
        auditService.event(AuditEvent.SESSION_REVOKED)
                .target("SESSION", sessionId)
                .record();
        sessionService.revokeOwn(user.id(), sessionId);
    }
}
