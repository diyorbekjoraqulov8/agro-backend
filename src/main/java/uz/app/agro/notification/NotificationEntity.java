package uz.app.agro.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Outbox yozuvi — o'chirilmaydi (jurnal); faqat holati o'zgaradi
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, updatable = false)
    private Long farmerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ChannelType channel = ChannelType.TELEGRAM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    private NotificationCategory category;

    @Column(nullable = false, length = 48, updatable = false)
    private String template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> params = new HashMap<>();

    @Column(length = 500, updatable = false)
    private String preview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "campaign_id", updatable = false)
    private Long campaignId;

    @Column(name = "ref_type", length = 32, updatable = false)
    private String refType;

    @Column(name = "ref_id", updatable = false)
    private Long refId;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
