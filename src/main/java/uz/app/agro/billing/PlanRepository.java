package uz.app.agro.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<PlanEntity, Long> {

    Optional<PlanEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<PlanEntity> findAllByOrderBySystemPlanDescContactSalesAscPriceAsc();
}
