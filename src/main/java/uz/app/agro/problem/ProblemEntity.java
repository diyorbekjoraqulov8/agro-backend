package uz.app.agro.problem;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** BaseEntity'dan meros olmaydi (audit kabi): yozuv faqat ProblemWriter upsert'i bilan, soft delete yo'q */
@Getter
@Setter
@Entity
@Table(name = "problems")
public class ProblemEntity {

    public enum Level { ERROR, WARN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProblemSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level level;

    @Column(name = "farmer_id")
    private Long farmerId;

    @Column(name = "farmer_key", nullable = false)
    private Long farmerKey;

    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(nullable = false)
    private int occurrences;

    @Column(name = "first_seen", nullable = false)
    private LocalDateTime firstSeen;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by_email", length = 120)
    private String resolvedByEmail;
}
