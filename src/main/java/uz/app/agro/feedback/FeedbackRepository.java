package uz.app.agro.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long>, JpaSpecificationExecutor<FeedbackEntity> {

    long countByFarmerIdAndCreatedDateAfter(Long farmerId, LocalDateTime after);

    long countByStatus(FeedbackStatus status);

    long countByStatusAndAssigneeId(FeedbackStatus status, Long assigneeId);

    @Query("SELECT coalesce(sum(f.unreadStaff), 0) FROM FeedbackEntity f WHERE f.status = uz.app.agro.feedback.FeedbackStatus.OPEN AND f.assigneeId = :staffId")
    long unreadFor(@Param("staffId") Long staffId);

    /** Fermerning ochiq suhbati (uq_feedback_farmer_active — ko'pi bilan bitta) */
    @Query("SELECT f FROM FeedbackEntity f WHERE f.farmer.id = :farmerId AND f.status <> uz.app.agro.feedback.FeedbackStatus.CLOSED")
    Optional<FeedbackEntity> findActive(@Param("farmerId") Long farmerId);

    /**
     * Navbatdan olish — ATOMIK: ikki operator bir vaqtda bossa, biriniki 1, ikkinchisiniki 0 qaytaradi.
     * Read-modify-write bo'lsa ikkalasi ham "oldim" deb o'ylardi (CLAUDE.md 5.3 — lost update).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FeedbackEntity f SET f.status = uz.app.agro.feedback.FeedbackStatus.OPEN, f.assigneeId = :staffId, f.assigneeEmail = :email,
                   f.assignedAt = :now, f.updatedDate = :now
            WHERE f.id = :id AND f.status = uz.app.agro.feedback.FeedbackStatus.NEW
            """)
    int claim(@Param("id") Long id, @Param("staffId") Long staffId, @Param("email") String email, @Param("now") LocalDateTime now);

    /** Fermer xabari — o'qilmaganlar soni atomik (+1); yopilgan suhbatga yozilmaydi (0 qaytadi) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FeedbackEntity f SET f.unreadStaff = f.unreadStaff + 1, f.lastMessageAt = :now,
                   f.lastSender = uz.app.agro.feedback.FeedbackSender.FARMER, f.updatedDate = :now
            WHERE f.id = :id AND f.status <> uz.app.agro.feedback.FeedbackStatus.CLOSED
            """)
    int touchByFarmer(@Param("id") Long id, @Param("now") LocalDateTime now);

    interface OperatorRow {
        Long getStaffId();
        String getEmail();
        long getOpenCount();
        long getClosedCount();
        long getRatedCount();
        Double getAvgRating();
    }

    @Query(value = """
            SELECT assignee_id AS staffId, max(assignee_email) AS email,
                   count(*) FILTER (WHERE status = 'OPEN')   AS openCount,
                   count(*) FILTER (WHERE status = 'CLOSED') AS closedCount,
                   count(rating)                             AS ratedCount,
                   avg(rating)::float8                       AS avgRating
            FROM feedback WHERE assignee_id IS NOT NULL
            GROUP BY assignee_id ORDER BY max(assignee_email)
            """, nativeQuery = true)
    List<OperatorRow> operators();
}
