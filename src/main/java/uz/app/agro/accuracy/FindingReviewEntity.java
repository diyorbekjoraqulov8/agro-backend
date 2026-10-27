package uz.app.agro.accuracy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Tizim xulosasiga xodim bahosi — bitta xulosaga bitta (qayta baholansa ustiga yoziladi).
 * BaseEntity'dan meros olmaydi: soft delete kerak emas, UNIQUE (target_type, target_id) oddiy bo'lishi uchun
 */
@Entity
@Table(name = "finding_reviews")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class FindingReviewEntity {

    public enum TargetType { ANALYSIS, MONITORING }

    public enum Verdict { CORRECT, PARTIAL, WRONG }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Verdict verdict;

    @Column(length = 1000)
    private String note;

    @Column(name = "reviewer_email")
    private String reviewerEmail;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
