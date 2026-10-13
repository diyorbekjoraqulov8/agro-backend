package uz.app.agro.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// BaseEntity'dan meros YO'Q: soft delete kerak emas (obunalar rejaga bog'langan) — active=false
@Entity
@Table(name = "plans")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class PlanEntity {

    /** To'lov yo'q bo'lganda qo'llanadigan tizim rejasi */
    public static final String FREE = "FREE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "UZS";

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "max_field_ha", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxFieldHa;

    @Column(name = "max_fields")
    private Integer maxFields;                       // null — cheksiz

    @Column(name = "max_analyses")
    private Integer maxAnalyses;                     // null — cheksiz

    @Column(name = "system_plan", nullable = false, updatable = false)
    private boolean systemPlan;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
