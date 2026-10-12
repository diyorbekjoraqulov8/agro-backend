package uz.app.agro.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FeedbackMessageRepository extends JpaRepository<FeedbackMessageEntity, Long> {

    List<FeedbackMessageEntity> findByFeedbackIdAndIdGreaterThanOrderByIdAsc(Long feedbackId, Long afterId);

    interface FileUseRow {
        String getHashId();
        Long getFeedbackId();
        String getFarmerName();
        String getFarmerUsername();
    }

    @Query("""
            SELECT DISTINCT m.fileHashId AS hashId, f.id AS feedbackId, fr.fullName AS farmerName, fr.telegramUsername AS farmerUsername
            FROM FeedbackMessageEntity m, FeedbackEntity f JOIN f.farmer fr
            WHERE m.feedbackId = f.id AND m.fileHashId IN :hashIds
            """)
    List<FileUseRow> fileUses(@Param("hashIds") Collection<String> hashIds);

    /** Eski Telegram rasmlari — hali serverga ko'chirilmagan (idx_feedback_messages_legacy_photo) */
    @Query("SELECT m FROM FeedbackMessageEntity m WHERE m.photoFileId IS NOT NULL AND m.fileHashId IS NULL ORDER BY m.id")
    List<FeedbackMessageEntity> findLegacyPhotos(org.springframework.data.domain.Pageable page);

    /** Ikki instans bir vaqtda ko'chirsa — faqat birinchisi yoziladi */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedbackMessageEntity m SET m.fileHashId = :hashId WHERE m.id = :id AND m.fileHashId IS NULL")
    int attachFile(@Param("id") Long id, @Param("hashId") String hashId);

    /** Spam himoyasi: fermerning barcha suhbatlaridagi xabarlari */
    @Query("""
            SELECT count(m) FROM FeedbackMessageEntity m, FeedbackEntity f
            WHERE m.feedbackId = f.id AND f.farmer.id = :farmerId
              AND m.sender = uz.app.agro.feedback.FeedbackSender.FARMER AND m.createdDate > :after
            """)
    long countFarmerMessagesSince(@Param("farmerId") Long farmerId, @Param("after") LocalDateTime after);
}
