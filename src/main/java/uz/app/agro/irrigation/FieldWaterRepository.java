package uz.app.agro.irrigation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FieldWaterRepository extends JpaRepository<FieldWaterEntity, Long> {

    /**
     * Eslatma nomzodlari: yoqilgan, sozlangan, dala o'chirilmagan, fermer botni bloklamagan.
     * Native — dalaning soft delete'i (@SQLRestriction) native'ga qo'llanmaydi, qo'lda.
     */
    @Query(value = """
            SELECT w.field_id FROM field_water w
              JOIN fields f  ON f.id = w.field_id AND f.deleted = FALSE
              JOIN farmers a ON a.id = f.farmer_id AND a.deleted = FALSE AND a.blocked_bot = FALSE
             WHERE w.reminders_enabled = TRUE AND w.last_irrigated_on IS NOT NULL
            """, nativeQuery = true)
    List<Long> activeFieldIds();

    /** Eslatma kimga — dala egasi (native: o'chirilgan dala/fermer filtri activeFieldIds'da) */
    @Query(value = "SELECT f.farmer_id FROM fields f WHERE f.id = :fieldId", nativeQuery = true)
    long farmerIdOf(@org.springframework.data.repository.query.Param("fieldId") long fieldId);
}
