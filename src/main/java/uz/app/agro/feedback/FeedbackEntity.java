package uz.app.agro.feedback;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uz.app.agro.farmer.FarmerEntity;

import java.time.LocalDateTime;

/**
 * Murojaat — fermer va operator suhbati. O'chirilmaydi (BaseEntity'dan meros yo'q), faqat CLOSED.
 * Xabarlar — FeedbackMessageEntity. assignee_id da FK yo'q (audit kabi — xodim o'chsa ham tarix qoladi).
 */
@Entity
@Table(name = "feedback")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class FeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id", nullable = false, updatable = false)
    private FarmerEntity farmer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private FeedbackKind kind;

    /** Birinchi xabar boshi — ro'yxatlar uchun */
    @Column(nullable = false, length = 200, updatable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackStatus status = FeedbackStatus.NEW;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "assignee_email", length = 120)
    private String assigneeEmail;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sender", length = 8)
    private FeedbackSender lastSender;

    /** Operator o'qimagan fermer xabarlari */
    @Column(name = "unread_staff", nullable = false)
    private int unreadStaff;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_by", length = 8)
    private FeedbackSender closedBy;

    @Column(name = "closed_by_email", length = 120)
    private String closedByEmail;

    @Column
    private Short rating;

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
