package uz.app.agro.accuracy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ObservationRepository extends JpaRepository<ObservationEntity, Long> {

    int REPORT_LIMIT = 1000;

    List<ObservationEntity> findByFieldIdAndObservedOnBetweenOrderByObservedOn(Long fieldId, LocalDate from, LocalDate to);

    interface Row {
        Long getId();
        Long getFieldId();
        String getFieldName();
        boolean getLab();
    }

    /** Aniqlik hisoboti: kuzatuv + dala nomi + Lab belgisi. lab null — hammasi. O'chirilgan dala kirmaydi */
    @Query(value = """
            SELECT o.id AS id, o.field_id AS fieldId, f.name AS fieldName, fa.lab AS lab
              FROM field_observations o
              JOIN fields f ON f.id = o.field_id AND f.deleted = FALSE
              JOIN farmers fa ON fa.id = f.farmer_id
             WHERE o.deleted = FALSE AND (CAST(:lab AS text) IS NULL OR fa.lab = CAST(CAST(:lab AS text) AS boolean))
             ORDER BY o.observed_on DESC, o.id DESC
             LIMIT 1000
            """, nativeQuery = true)
    List<Row> report(@Param("lab") String lab);   // "true"/"false"/null — loyiha patterni: null uchun CAST AS text
}
