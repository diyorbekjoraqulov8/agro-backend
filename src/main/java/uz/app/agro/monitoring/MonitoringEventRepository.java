package uz.app.agro.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonitoringEventRepository extends JpaRepository<MonitoringEventEntity, Long> {

    boolean existsByFieldIdAndSceneDate(Long fieldId, LocalDate sceneDate);

    List<MonitoringEventEntity> findTop20ByFieldIdOrderBySceneDateDesc(Long fieldId);

    List<MonitoringEventEntity> findByFieldIdInAndSceneDateBetweenOrderBySceneDate(Collection<Long> fieldIds,
                                                                                   java.time.LocalDate from, java.time.LocalDate to);

    List<MonitoringEventEntity> findByFieldIdAndSceneDateBetweenOrderBySceneDate(Long fieldId, java.time.LocalDate from,
                                                                                 java.time.LocalDate to);

    Optional<MonitoringEventEntity> findFirstByFieldIdOrderBySceneDateDesc(Long fieldId);

    /** Trend uchun: shu sanadan oldingi (yoki o'sha) eng yangi surat */
    Optional<MonitoringEventEntity> findFirstByFieldIdAndSceneDateLessThanEqualOrderBySceneDateDesc(Long fieldId, java.time.LocalDate day);

    interface FileUseRow {
        String getHashId();
        Long getFieldId();
        String getFieldName();
        LocalDate getSceneDate();
    }

    /** "Fayllar" sahifasi: xarita qaysi dalaning monitoringi */
    @Query(value = """
            SELECT e.map_file_hash_id AS hashId, e.field_id AS fieldId, f.name AS fieldName, e.scene_date AS sceneDate
              FROM monitoring_events e JOIN fields f ON f.id = e.field_id
             WHERE e.map_file_hash_id IN (:hashIds)
            """, nativeQuery = true)
    List<FileUseRow> fileUses(@Param("hashIds") Collection<String> hashIds);
}
