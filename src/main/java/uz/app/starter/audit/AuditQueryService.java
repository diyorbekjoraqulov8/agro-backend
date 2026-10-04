package uz.app.starter.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.starter.common.PageResponse;
import uz.app.starter.rbac.Permissions;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    @PreAuthorize(Permissions.CAN_READ_AUDIT)
    public PageResponse<AuditLogResponse> search(
            AuditEvent eventType,
            Long actorId,
            Pageable pageable
    ) {
        Page<AuditLogResponse> page = auditLogRepository
                .findAll(AuditSpecs.filter(eventType, actorId), pageable)
                .map(this::toResponse);
        return PageResponse.of(page);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getEventType(), log.getOutcome().toString(),
                log.getActorId(), log.getActorEmail(),
                log.getTargetType(), log.getTargetId(),
                log.getIp(), log.getUserAgent(),
                log.getDetails(), log.getCreatedAt()
        );
    }
}
