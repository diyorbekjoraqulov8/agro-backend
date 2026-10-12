package uz.app.agro.field;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.List;

public interface FieldRepository extends JpaRepository<FieldEntity, Long>,
                                         JpaSpecificationExecutor<FieldEntity> {

    List<FieldEntity> findByFarmerIdOrderByIdDesc(Long farmerId);

    long countByFarmerId(Long farmerId);

    List<FieldEntity> findByFarmerTelegramUserIdOrderByIdDesc(Long telegramUserId);

    interface LastJobs {
        Long getFieldId();
        Long getLastJobId();
        String getLastStatus();
        LocalDateTime getLastAt();
        Long getLastDoneJobId();
    }

    /** Har dala uchun oxirgi tahlil va oxirgi TAYYOR tahlil — bitta so'rov */
    @Query(value = """
            SELECT f.id AS fieldId,
                   (SELECT j.id FROM analysis_jobs j WHERE j.field_id = f.id ORDER BY j.id DESC LIMIT 1) AS lastJobId,
                   (SELECT j.status FROM analysis_jobs j WHERE j.field_id = f.id ORDER BY j.id DESC LIMIT 1) AS lastStatus,
                   (SELECT coalesce(j.finished_at, j.created_date) FROM analysis_jobs j WHERE j.field_id = f.id ORDER BY j.id DESC LIMIT 1) AS lastAt,
                   (SELECT j.id FROM analysis_jobs j WHERE j.field_id = f.id AND j.status = 'DONE' ORDER BY j.id DESC LIMIT 1) AS lastDoneJobId
              FROM fields f WHERE f.id IN (:ids)
            """, nativeQuery = true)
    List<LastJobs> lastJobs(@Param("ids") List<Long> ids);
}
