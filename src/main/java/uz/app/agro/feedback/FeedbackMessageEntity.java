package uz.app.agro.feedback;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Append-only: tahrirlanmaydi, o'chirilmaydi (faqat delivered belgilanadi)
@Entity
@Table(name = "feedback_messages")
@Getter
@Setter
public class FeedbackMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id", nullable = false, updatable = false)
    private Long feedbackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private FeedbackSender sender;

    @Column(name = "staff_id", updatable = false)
    private Long staffId;

    @Column(name = "staff_email", length = 120, updatable = false)
    private String staffEmail;

    @Column(columnDefinition = "text", updatable = false)
    private String text;

    /** Fayl (rasm/PDF) — files.hash_id */
    @Column(name = "file_hash_id", length = 32)
    private String fileHashId;

    /** Eski: Telegram file_id. Fonda files'ga ko'chiriladi (FeedbackFileBackfill), keyin fileHashId to'ladi */
    @Column(name = "photo_file_id", length = 256, updatable = false)
    private String photoFileId;

    @Column
    private Boolean delivered;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();
}
