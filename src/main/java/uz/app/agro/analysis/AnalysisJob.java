package uz.app.agro.analysis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uz.app.agro.field.FieldEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

// BaseEntity'dan meros YO'Q: navbat tarixi o'chirilmaydi (AuditLog mantiqi)
@Entity
@Table(name = "analysis_jobs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false)
    private FieldEntity field;

    // V8/V11 dagi CHECK cheklovlari bilan bir xil bo'lishi SHART
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind = Kind.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    // Tahlil paytidagi ekin (V14) — dala ekini keyin o'zgarsa ham eski hisobot to'g'ri qoladi
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private uz.app.agro.field.Crop crop;

    @Column(name = "season_start", nullable = false)
    private LocalDate seasonStart;

    @Column(name = "season_end", nullable = false)
    private LocalDate seasonEnd;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_try_at", nullable = false)
    private LocalDateTime nextTryAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(length = 500)
    private String error;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    // STAFF — admin paneldan (V11); fermerning bepul kvotasiga kirmaydi
    public enum Kind { FREE, PAID, STAFF }

    public enum Status { PENDING, RUNNING, DONE, FAILED }
}
