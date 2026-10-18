package uz.app.agro.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uz.app.agro.farmer.FarmerEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Moliyaviy yozuv — o'chirilmaydi (BaseEntity'dan meros yo'q), faqat CANCELLED
@Entity
@Table(name = "farmer_subscriptions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class SubscriptionEntity {

    public enum Status { ACTIVE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id", nullable = false, updatable = false)
    private FarmerEntity farmer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private PlanEntity plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Column(columnDefinition = "text")
    private String note;

    /** Qaysi chegirma bilan (V38); null — chegirmasiz */
    @Column(name = "discount_id")
    private Long discountId;

    @Column(name = "recorded_by_id")
    private Long recordedById;

    @Column(name = "recorded_by_email", length = 120)
    private String recordedByEmail;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    public boolean isActiveOn(LocalDate day) {
        return status == Status.ACTIVE && !day.isBefore(validFrom) && !day.isAfter(validUntil);
    }
}
