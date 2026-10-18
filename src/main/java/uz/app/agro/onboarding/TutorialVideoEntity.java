package uz.app.agro.onboarding;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Yo'riqnoma videosi (til bo'yicha). BaseEntity'siz: kalit — til, soft delete kerak emas (almashtiriladi) */
@Getter
@Setter
@Entity
@Table(name = "tutorial_videos")
public class TutorialVideoEntity {

    @Id
    @Column(length = 5)
    private String language;

    @Column(name = "file_hash_id", nullable = false, length = 32)
    private String fileHashId;

    @Column(name = "telegram_file_id")
    private String telegramFileId;

    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate;

    @Column(name = "updated_by_email", length = 120)
    private String updatedByEmail;
}
