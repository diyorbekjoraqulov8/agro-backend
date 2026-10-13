package uz.app.agro.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FieldMonitoringRepository extends JpaRepository<FieldMonitoringEntity, Long> {

    /**
     * Kuzatiladigan dala: o'chirilmagan, fermer botni bloklamagan va (paidOnly bo'lsa) bugun faol pullik obunasi bor.
     * Bitta joyda — enroll va due ikkalasi ishlatadi.
     */
    String ELIGIBLE = """
            f.deleted = FALSE AND fa.deleted = FALSE AND fa.blocked_bot = FALSE
            AND (:paidOnly = FALSE OR EXISTS (SELECT 1 FROM farmer_subscriptions s WHERE s.farmer_id = fa.id
                 AND s.status = 'ACTIVE' AND CURRENT_DATE BETWEEN s.valid_from AND s.valid_until))
            """;

    /** Yangi kuzatiladigan dalalar navbatga (darhol tekshiriladi) */
    @Modifying
    @Query(value = "INSERT INTO field_monitoring (field_id) SELECT f.id FROM fields f JOIN farmers fa ON fa.id = f.farmer_id WHERE "
            + ELIGIBLE + " ON CONFLICT (field_id) DO NOTHING", nativeQuery = true)
    int enroll(@Param("paidOnly") boolean paidOnly);

    /** Vaqti kelgan — eng uzoq kutganlari birinchi. Tarif tugagan dala navbatda qoladi, lekin tanlanmaydi */
    @Query(value = "SELECT fm.field_id FROM field_monitoring fm JOIN fields f ON f.id = fm.field_id JOIN farmers fa ON fa.id = f.farmer_id"
            + " WHERE fm.next_check_at <= now() AND " + ELIGIBLE + " ORDER BY fm.next_check_at LIMIT :limit", nativeQuery = true)
    List<Long> due(@Param("paidOnly") boolean paidOnly, @Param("limit") int limit);
}
