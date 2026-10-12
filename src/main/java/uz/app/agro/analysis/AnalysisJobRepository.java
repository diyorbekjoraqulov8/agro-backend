package uz.app.agro.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long>,
                                               JpaSpecificationExecutor<AnalysisJob> {

    boolean existsByFieldIdAndStatusIn(Long fieldId, Collection<AnalysisJob.Status> statuses);

    java.util.Optional<AnalysisJob> findTopByFieldIdAndStatusOrderByIdDesc(Long fieldId, AnalysisJob.Status status);

    // Faqat tranzaksiya ICHIDA chaqiriladi — aks holda qulf darhol yechiladi.
    // SKIP LOCKED: ikkinchi instans bir xil job'ni olmaydi
    @Query(value = """
            SELECT * FROM analysis_jobs
             WHERE status = 'PENDING' AND next_try_at <= now()
             ORDER BY next_try_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AnalysisJob> lockNextBatch(@Param("limit") int limit);

    // Ilova RUNNING paytida o'chib qolsa — job'lar navbatga qaytadi
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AnalysisJob j
               SET j.status = uz.app.agro.analysis.AnalysisJob.Status.PENDING,
                   j.nextTryAt = CURRENT_TIMESTAMP
             WHERE j.status = uz.app.agro.analysis.AnalysisJob.Status.RUNNING
               AND j.startedAt < :before
            """)
    int requeueStuck(@Param("before") LocalDateTime before);

    // Native: JPQL join FieldEntity'ning @SQLRestriction'ini qo'llaydi — o'chirilgan dala
    // hisobdan tushib, fermer yana tahlil olardi.
    // FAILED hisoblanmaydi: poligon yaroqsiz / texnik xato — kvotani yemaydi

    /** FREE tarif: umrbod bepul tahlillar soni */
    @Query(value = """
            SELECT count(*) FROM analysis_jobs j
              JOIN fields f ON f.id = j.field_id
             WHERE f.farmer_id = :farmerId AND j.kind = 'FREE' AND j.status <> 'FAILED'
            """, nativeQuery = true)
    long countFreeJobs(@Param("farmerId") Long farmerId);

    /** Pullik tarif: obuna davridagi tahlillar soni [from, to] */
    @Query(value = """
            SELECT count(*) FROM analysis_jobs j
              JOIN fields f ON f.id = j.field_id
             WHERE f.farmer_id = :farmerId AND j.kind = 'PAID' AND j.status <> 'FAILED'
               AND j.created_date >= :from AND j.created_date < :toExclusive
            """, nativeQuery = true)
    long countPaidJobs(@Param("farmerId") Long farmerId,
                       @Param("from") java.time.LocalDateTime from,
                       @Param("toExclusive") java.time.LocalDateTime toExclusive);
}
