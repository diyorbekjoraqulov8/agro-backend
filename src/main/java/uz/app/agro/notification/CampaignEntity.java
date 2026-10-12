package uz.app.agro.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "notification_campaigns")
@Getter
@Setter
public class CampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> texts = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "button_texts", columnDefinition = "jsonb")
    private Map<String, String> buttonTexts;

    @Column(name = "button_url", length = 500)
    private String buttonUrl;

    /** Audience — JSON ko'rinishida (tarix: kimga yuborilgani o'zgarmaydi) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> audience = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CampaignStatus status = CampaignStatus.SCHEDULED;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    private Integer total;

    @Column(name = "created_by_email", length = 120, updatable = false)
    private String createdByEmail;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
