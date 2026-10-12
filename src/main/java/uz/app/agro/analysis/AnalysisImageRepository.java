package uz.app.agro.analysis;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface AnalysisImageRepository extends JpaRepository<AnalysisImage, Long> {

    /** Hali files'ga ko'chirilmagan (idx_analysis_images_legacy) */
    /** Keyset: after'dan keyingilar — xato bergan qator keyingi paketlarni to'smaydi */
    @Query("SELECT i.jobId FROM AnalysisImage i WHERE i.mapFileHashId IS NULL AND i.jobId > :after ORDER BY i.jobId")
    List<Long> findLegacyIds(@Param("after") long after, Pageable page);

    /** Ikki instans bir vaqtda ko'chirsa — faqat birinchisi yoziladi; BYTEA bo'shatiladi (DB hajmi) */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AnalysisImage i SET i.mapFileHashId = :map, i.chartFileHashId = :chart, i.mapPng = NULL, i.chartPng = NULL
            WHERE i.jobId = :jobId AND i.mapFileHashId IS NULL
            """)
    int attachFiles(@Param("jobId") Long jobId, @Param("map") String map, @Param("chart") String chart);

    interface FileUseRow {
        String getHashId();
        Long getJobId();
        String getFieldName();
    }

    /** "Fayllar" sahifasi: fayl qaysi tahlilniki (dala o'chirilgan bo'lsa ham — native, @SQLRestriction'siz) */
    @Query(value = """
            SELECT x.hash_id AS hashId, x.job_id AS jobId, f.name AS fieldName
            FROM (SELECT job_id, map_file_hash_id AS hash_id FROM analysis_images WHERE map_file_hash_id IN (:hashIds)
                  UNION ALL
                  SELECT job_id, chart_file_hash_id FROM analysis_images WHERE chart_file_hash_id IN (:hashIds)) x
            JOIN analysis_jobs j ON j.id = x.job_id
            LEFT JOIN fields f ON f.id = j.field_id
            """, nativeQuery = true)
    List<FileUseRow> fileUses(@Param("hashIds") Collection<String> hashIds);
}
