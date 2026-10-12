package uz.app.agro.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OnboardingStepRepository extends JpaRepository<OnboardingStepEntity, Long> {

    List<OnboardingStepEntity> findAllByOrderByPositionAscIdAsc();

    Optional<OnboardingStepEntity> findByCode(String code);

    boolean existsByCode(String code);

    /** Maxsus savol javobi — qayta javob berilsa yangilanadi */
    @Modifying
    @Query(value = """
            INSERT INTO farmer_answers (farmer_id, step_code, value, answered_at) VALUES (:farmerId, :code, :value, now())
            ON CONFLICT (farmer_id, step_code) DO UPDATE SET value = EXCLUDED.value, answered_at = now()
            """, nativeQuery = true)
    void saveAnswer(@Param("farmerId") Long farmerId, @Param("code") String code, @Param("value") String value);

    interface AnswerRow {
        String getCode();
        String getValue();
    }

    @Query(value = "SELECT step_code AS code, value FROM farmer_answers WHERE farmer_id = :farmerId", nativeQuery = true)
    List<AnswerRow> answers(@Param("farmerId") Long farmerId);
}
