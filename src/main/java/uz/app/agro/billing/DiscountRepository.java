package uz.app.agro.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DiscountRepository extends JpaRepository<DiscountEntity, Long> {

    List<DiscountEntity> findAllByOrderByActiveDescStartsOnDescIdDesc();

    /** Bugun amal qilayotganlar (yoqilgan, sana oralig'ida) */
    @Query("""
            SELECT d FROM DiscountEntity d
             WHERE d.active = true AND d.startsOn <= :day AND (d.endsOn IS NULL OR d.endsOn >= :day)
            """)
    List<DiscountEntity> findActiveOn(@Param("day") LocalDate day);
}
