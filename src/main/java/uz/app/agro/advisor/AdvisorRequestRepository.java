package uz.app.agro.advisor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AdvisorRequestRepository extends JpaRepository<AdvisorRequestEntity, Long>, JpaSpecificationExecutor<AdvisorRequestEntity> {

    /** SKIP LOCKED: ikkinchi instans shu so'rovni olmaydi (tahlil va bildirishnoma navbati bilan bir xil pattern) */
    @Query(value = """
            SELECT id FROM advisor_requests
             WHERE status = 'PENDING' AND next_attempt_at <= now()
             ORDER BY next_attempt_at, id LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockDue(@Param("limit") int limit);

    /** Kvota: AI javob bermagani (FAILED) hisoblanmaydi — fermer aybsiz */
    long countByFarmerIdAndStatusNot(Long farmerId, AdvisorRequestEntity.Status status);

    long countByFarmerIdAndStatusNotAndCreatedDateAfter(Long farmerId, AdvisorRequestEntity.Status status, LocalDateTime after);

    interface StatusCount {
        String getKey();
        long getCount();
    }

    @Query(value = "SELECT status AS key, count(*) AS count FROM advisor_requests WHERE created_date >= :since GROUP BY status", nativeQuery = true)
    List<StatusCount> statusCounts(@Param("since") LocalDateTime since);

    /** AI xulosasi turlari (kasallik, zararkunanda…) — qaysi muammolar ko'p */
    @Query(value = """
            SELECT ai_result ->> 'category' AS key, count(*) AS count FROM advisor_requests
             WHERE status = 'ANSWERED' AND ai_result ->> 'category' IS NOT NULL AND created_date >= :since GROUP BY 1
            """, nativeQuery = true)
    List<StatusCount> categoryCounts(@Param("since") LocalDateTime since);

    interface ModelTokensRow {
        String getModel();
        long getInputTokens();
        long getOutputTokens();
        long getRequests();
    }

    @Query(value = """
            SELECT ai_model AS model, coalesce(sum(input_tokens), 0) AS inputTokens, coalesce(sum(output_tokens), 0) AS outputTokens,
                   count(*) AS requests
              FROM advisor_requests WHERE ai_model IS NOT NULL AND created_date >= :since GROUP BY ai_model
            """, nativeQuery = true)
    List<ModelTokensRow> tokensByModel(@Param("since") LocalDateTime since);

    interface FileUseRow {
        String getHashId();
        Long getId();
    }

    @Query(value = "SELECT photo_hash_id AS hashId, id FROM advisor_requests WHERE photo_hash_id IN (:hashIds)", nativeQuery = true)
    List<FileUseRow> fileUses(@Param("hashIds") Collection<String> hashIds);
}
