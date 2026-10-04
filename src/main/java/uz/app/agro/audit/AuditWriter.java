package uz.app.agro.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uz.app.agro.security.CurrentActor;

@Component
@RequiredArgsConstructor
class AuditWriter {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AuditLog entry) {
        enrich(entry);
        repository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveIndependently(AuditLog entry) {
        enrich(entry);
        repository.save(entry);
    }

    private void enrich(AuditLog entry) {
        if (entry.getActorId() == null) {
            CurrentActor.get().ifPresent(user -> {
                entry.setActorId(user.id());
                entry.setActorEmail(user.email());
            });
        }
        if (entry.getIp() == null && RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            entry.setIp(req.getRemoteAddr());
            entry.setUserAgent(req.getHeader("User-Agent"));
        }
    }
}