package uz.app.agro.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private AuditEvent eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Outcome outcome;

    @Column(name = "actor_id")     private Long actorId;
    @Column(name = "actor_email", length = 120) private String actorEmail;
    @Column(name = "target_type", length = 64)  private String targetType;
    @Column(name = "target_id", length = 64)    private String targetId;
    @Column(length = 45)  private String ip;
    @Column(name = "user_agent", length = 255) private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Outcome { SUCCESS, FAILURE }

    public static AuditLog event(AuditEvent eventType, Outcome outcome) {
        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setOutcome(outcome);
        return log;
    }
}