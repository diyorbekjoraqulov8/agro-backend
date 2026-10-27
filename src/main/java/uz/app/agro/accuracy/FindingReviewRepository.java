package uz.app.agro.accuracy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FindingReviewRepository extends JpaRepository<FindingReviewEntity, Long> {

    Optional<FindingReviewEntity> findByTargetTypeAndTargetId(FindingReviewEntity.TargetType type, Long targetId);

    List<FindingReviewEntity> findByFieldId(Long fieldId);

    /**
     * Atomik upsert: ikki xodim/ikki bosish bir vaqtda birinchi bahoni yuborsa find→INSERT poygasi UNIQUE'ga urilib
     * 409 berardi. clearAutomatically — keyingi findBy yangi qiymatni o'qisin
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO finding_reviews (target_type, target_id, field_id, verdict, note, reviewer_email, created_date, updated_date)
            VALUES (:type, :targetId, :fieldId, :verdict, :note, :reviewer, now(), now())
            ON CONFLICT (target_type, target_id) DO UPDATE
               SET field_id = EXCLUDED.field_id, verdict = EXCLUDED.verdict, note = EXCLUDED.note,
                   reviewer_email = EXCLUDED.reviewer_email, updated_date = now()
            """, nativeQuery = true)
    void upsert(@Param("type") String type, @Param("targetId") long targetId, @Param("fieldId") long fieldId,
                @Param("verdict") String verdict, @Param("note") String note, @Param("reviewer") String reviewer);

    interface VerdictCount {
        String getTargetType();
        String getVerdict();
        boolean getLab();
        long getCount();
    }

    /** Qo'lda baholar soni: xulosa turi × baho × Lab. O'chirilgan dala kirmaydi */
    @Query(value = """
            SELECT r.target_type AS targetType, r.verdict AS verdict, fa.lab AS lab, count(*) AS count
              FROM finding_reviews r
              JOIN fields f ON f.id = r.field_id AND f.deleted = FALSE
              JOIN farmers fa ON fa.id = f.farmer_id
             WHERE CAST(:lab AS text) IS NULL OR fa.lab = CAST(CAST(:lab AS text) AS boolean)
             GROUP BY r.target_type, r.verdict, fa.lab
            """, nativeQuery = true)
    List<VerdictCount> counts(@Param("lab") String lab);   // "true"/"false"/null — loyiha patterni: null uchun CAST AS text
}
