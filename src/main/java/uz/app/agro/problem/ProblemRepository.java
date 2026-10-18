package uz.app.agro.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProblemRepository extends JpaRepository<ProblemEntity, Long>, JpaSpecificationExecutor<ProblemEntity> {

    /** Ochiq bir xil xato bo'lsa — sanoq va oxirgi holat yangilanadi (atomik, poyga yo'q) */
    @Modifying
    @Query(value = """
            INSERT INTO problems (source, level, farmer_id, farmer_key, field_id, ref_type, ref_id, fingerprint, message, detail)
            VALUES (:source, :level, :farmerId, :farmerKey, :fieldId, :refType, :refId, :fingerprint, :message, :detail)
            ON CONFLICT (source, farmer_key, fingerprint) WHERE resolved_at IS NULL DO UPDATE
               SET occurrences = problems.occurrences + 1, last_seen = now(), level = EXCLUDED.level,
                   field_id = COALESCE(EXCLUDED.field_id, problems.field_id),
                   ref_type = COALESCE(EXCLUDED.ref_type, problems.ref_type),
                   ref_id = COALESCE(EXCLUDED.ref_id, problems.ref_id),
                   message = EXCLUDED.message, detail = COALESCE(EXCLUDED.detail, problems.detail)
            """, nativeQuery = true)
    void upsert(@Param("source") String source, @Param("level") String level, @Param("farmerId") Long farmerId,
                @Param("farmerKey") long farmerKey, @Param("fieldId") Long fieldId, @Param("refType") String refType,
                @Param("refId") Long refId, @Param("fingerprint") String fingerprint, @Param("message") String message,
                @Param("detail") String detail);

    interface SourceCount {
        String getSource();
        long getOpen();
        long getRecent();
        LocalDateTime getLastSeen();
    }

    /** Har manba: ochiq muammolar va oxirgi 24 soatda takrorlanganlar */
    @Query(value = """
            SELECT source, count(*) FILTER (WHERE resolved_at IS NULL) AS open,
                   count(*) FILTER (WHERE resolved_at IS NULL AND last_seen > :since) AS recent, max(last_seen) AS "lastSeen"
              FROM problems GROUP BY source
            """, nativeQuery = true)
    List<SourceCount> summary(@Param("since") LocalDateTime since);

    @Query("SELECT count(p) FROM ProblemEntity p WHERE p.resolvedAt IS NULL AND p.level = uz.app.agro.problem.ProblemEntity.Level.ERROR AND p.lastSeen > :since")
    long countRecentErrors(@Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM ProblemEntity p WHERE p.resolvedAt IS NOT NULL AND p.resolvedAt < :before")
    int deleteResolvedBefore(@Param("before") LocalDateTime before);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProblemEntity p SET p.resolvedAt = :now, p.resolvedByEmail = :by WHERE p.resolvedAt IS NULL AND p.source = :source")
    int resolveAll(@Param("source") ProblemSource source, @Param("now") LocalDateTime now, @Param("by") String by);
}
