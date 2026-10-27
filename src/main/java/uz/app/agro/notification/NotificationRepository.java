package uz.app.agro.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long>, JpaSpecificationExecutor<NotificationEntity> {

    /**
     * Dala tarixi: shu dala haqidagi xabarlar (Lab dalada — SKIPPED, "fermerga ketgan bo'lardi"). Bog'lanish turlicha:
     * tahlil — params.jobId, monitoring — params.fieldId, sug'orish — params.status.fieldId, ob-havo — ref (fermer + katak)
     */
    @Query(value = """
            SELECT n.* FROM notifications n
             WHERE n.farmer_id = :farmerId AND n.created_date >= :from AND n.created_date < :to
               AND (n.params->>'jobId' IN (SELECT j.id::text FROM analysis_jobs j WHERE j.field_id = :fieldId)
                 OR (n.ref_type = 'MONITORING_EVENT' AND n.params->>'fieldId' = CAST(:fieldId AS text))
                 OR n.params->'status'->>'fieldId' = CAST(:fieldId AS text)
                 OR (n.ref_type = 'WEATHER_ALERT' AND n.ref_id IN
                     (SELECT a.id FROM weather_alerts a WHERE a.farmer_id = :farmerId AND a.cell_key = :cellKey)))
             ORDER BY n.created_date DESC
             LIMIT 500
            """, nativeQuery = true)
    List<NotificationEntity> forField(@Param("farmerId") long farmerId, @Param("fieldId") long fieldId,
                                      @Param("cellKey") String cellKey,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // SKIP LOCKED: ikkinchi instans bir xil xabarni olmaydi (analysis navbati bilan bir xil pattern)
    @Query(value = """
            SELECT * FROM notifications
             WHERE status = 'PENDING' AND next_attempt_at <= now() AND channel IN (:channels)
             ORDER BY next_attempt_at, id
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationEntity> lockNextBatch(@Param("channels") Collection<String> channels, @Param("limit") int limit);

    /** Ilova SENDING paytida o'chib qolsa — xabar navbatga qaytadi (Telegram'ga yetgan bo'lishi mumkin — kamdan-kam takror) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationEntity n SET n.status = uz.app.agro.notification.NotificationStatus.PENDING
             WHERE n.status = uz.app.agro.notification.NotificationStatus.SENDING AND n.nextAttemptAt < :before
            """)
    int requeueStuck(@Param("before") LocalDateTime before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationEntity n SET n.status = uz.app.agro.notification.NotificationStatus.CANCELLED
             WHERE n.campaignId = :campaignId AND n.status = uz.app.agro.notification.NotificationStatus.PENDING
            """)
    int cancelCampaign(@Param("campaignId") Long campaignId);

    interface StatusCount {
        String getStatus();
        long getCount();
    }

    interface CampaignStatusCount extends StatusCount {
        Long getCampaignId();
    }

    /** Sahifadagi barcha kampaniyalar uchun bitta so'rov (N+1 emas) */
    @Query(value = """
            SELECT campaign_id AS campaignId, status, count(*) AS count FROM notifications
             WHERE campaign_id IN (:ids) GROUP BY campaign_id, status
            """, nativeQuery = true)
    List<CampaignStatusCount> campaignCounts(@Param("ids") Collection<Long> campaignIds);

    @Query(value = "SELECT status, count(*) AS count FROM notifications WHERE created_date >= :since GROUP BY status", nativeQuery = true)
    List<StatusCount> countsSince(@Param("since") LocalDateTime since);


    boolean existsByRefTypeAndRefId(String refType, Long refId);
}
