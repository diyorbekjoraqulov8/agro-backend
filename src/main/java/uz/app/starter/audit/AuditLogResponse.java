package uz.app.starter.audit;


import java.time.LocalDateTime;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        AuditEvent eventType,
        String outcome,
        Long actorId,
        String actorEmail,
        String targetType,
        String targetId,
        String ip,
        String userAgent,
        Map<String, Object> details,
        LocalDateTime createdAt
) {}