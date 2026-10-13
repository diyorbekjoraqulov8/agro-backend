package uz.app.agro.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<CampaignEntity, Long> {

    @Query(value = """
            SELECT id FROM notification_campaigns
             WHERE status = 'SCHEDULED' AND scheduled_at <= now()
             ORDER BY scheduled_at LIMIT :limit
            """, nativeQuery = true)
    List<Long> dueIds(@Param("limit") int limit);

    /** SKIP LOCKED: ikki instans bir kampaniyani ikki marta tarqatmaydi; holat qayta tekshiriladi (cancel bilan poyga) */
    @Query(value = """
            SELECT * FROM notification_campaigns
             WHERE id = :id AND status = 'SCHEDULED'
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<CampaignEntity> lockDue(@Param("id") Long id);

    @Query("SELECT c.id FROM CampaignEntity c WHERE c.status = uz.app.agro.notification.CampaignStatus.SENDING")
    List<Long> sendingIds();

    /** Hammasi yetkazilgan bo'lsa DONE — shartli UPDATE: shu orada bekor qilingani ustidan yozilmaydi */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE notification_campaigns SET status = 'DONE', finished_at = now()
             WHERE id = :id AND status = 'SENDING'
               AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.campaign_id = :id AND n.status IN ('PENDING', 'SENDING'))
            """, nativeQuery = true)
    int finishIfDelivered(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE notification_campaigns SET status = 'CANCELLED', finished_at = now() WHERE id = :id AND status = 'SCHEDULED'",
            nativeQuery = true)
    int abort(@Param("id") Long id);
}
