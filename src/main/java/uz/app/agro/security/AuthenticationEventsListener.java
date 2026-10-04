package uz.app.agro.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;

@Component
@RequiredArgsConstructor
public class AuthenticationEventsListener {

    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = String.valueOf(event.getAuthentication().getPrincipal());
        loginAttemptService.onFailure(email);

        auditService.event(AuditEvent.LOGIN_FAILURE)
                .failure()
                .actorEmail(email)
                .recordIndependently();
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CustomUserDetails user) {
            loginAttemptService.onSuccess(user.email());

            auditService.event(AuditEvent.LOGIN_SUCCESS)
                    .actor(user.id(), user.email())
                    .recordIndependently();
        }
    }

    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        String email = String.valueOf(event.getAuthentication().getPrincipal());

        auditService.event(AuditEvent.ACCOUNT_LOCKED)
                .failure()
                .actorEmail(email)
                .recordIndependently();
    }
}