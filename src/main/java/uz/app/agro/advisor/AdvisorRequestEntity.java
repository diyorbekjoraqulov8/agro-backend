package uz.app.agro.advisor;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/** Fermer rasmi → AI tavsiyasi (yakuniy, agronom tekshiruvisiz). Navbat va tarix bitta jadvalda */
@Getter
@Setter
@Entity
@Table(name = "advisor_requests")
public class AdvisorRequestEntity {

    public enum Status { PENDING, ANSWERED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, updatable = false)
    private Long farmerId;

    @Column(name = "field_id", updatable = false)
    private Long fieldId;

    @Column(name = "photo_hash_id", nullable = false, updatable = false, length = 22)
    private String photoHashId;

    @Column(name = "farmer_note", length = 1000)
    private String farmerNote;

    @Column(nullable = false, length = 8)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "ai_model", length = 80)
    private String aiModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_result", columnDefinition = "jsonb")
    private Map<String, Object> aiResult;

    @Column(name = "ai_error", length = 500)
    private String aiError;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
}
