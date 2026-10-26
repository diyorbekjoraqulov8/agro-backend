package uz.app.agro.field;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dalaning bitta ekin mavsumi: ekin + ekish sanasi. Faol — bittadan ko'p emas (qisman unique index).
 * BaseEntity'dan meros olmaydi: o'chirilmaydi, yopiladi (FINISHED) — tarix.
 */
@Entity
@Table(name = "field_seasons")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class FieldSeasonEntity {

    /** UNANSWERED — hali so'ralmagan/javob yo'q; NOT_SOWN — dala tayyor, hali ekilmagan; UNKNOWN — fermer bilmaydi */
    public enum Sowing { UNANSWERED, SOWN, NOT_SOWN, UNKNOWN }

    public enum Status { ACTIVE, FINISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Crop crop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Sowing sowing = Sowing.UNANSWERED;

    @Column(name = "sown_on")
    private LocalDate sownOn;

    @Column(name = "sown_on_approx", nullable = false)
    private boolean sownOnApprox;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "finished_on")
    private LocalDate finishedOn;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
