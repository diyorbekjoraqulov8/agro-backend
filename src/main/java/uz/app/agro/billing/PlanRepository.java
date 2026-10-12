package uz.app.agro.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<PlanEntity, Long> {

    Optional<PlanEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<PlanEntity> findAllByOrderBySystemPlanDescPriceAsc();

    /** Botdagi "pullik tarifda N ga gacha" xabari uchun */
    @Query("SELECT max(p.maxFieldHa) FROM PlanEntity p WHERE p.active = true AND p.systemPlan = false")
    Optional<BigDecimal> maxPaidFieldHa();
}
