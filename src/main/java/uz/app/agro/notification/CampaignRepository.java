package uz.app.agro.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampaignRepository extends JpaRepository<CampaignEntity, Long> {

    /** Vaqti kelgan — SKIP LOCKED: ikki instans bir kampaniyani ikki marta tarqatmaydi */
    @Query(value = """
            SELECT * FROM notification_campaigns
             WHERE status = 'SCHEDULED' AND scheduled_at <= now()
             ORDER BY scheduled_at LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CampaignEntity> lockDue(@Param("limit") int limit);

    List<CampaignEntity> findByStatus(CampaignStatus status);
}
