package uz.app.agro.weather;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeatherAlertRepository extends JpaRepository<WeatherAlertEntity, Long> {

    /** Dala tarixi: ogohlantirish fermer + katak bo'yicha (bir katakdagi dalalar umumiy) */
    List<WeatherAlertEntity> findByFarmerIdAndCellKeyAndEventDateBetweenOrderByEventDate(Long farmerId, String cellKey,
                                                                                         LocalDate from, LocalDate to);

    Optional<WeatherAlertEntity> findByFarmerIdAndCellKeyAndKindAndEventDate(Long farmerId, String cellKey, WeatherRisk.Kind kind, LocalDate eventDate);

    /** Issiq — "to'lqin": har kuni emas, oldingi N kun ichida aytilgan bo'lsa (from..to — oldingi kunlar) */
    boolean existsByFarmerIdAndCellKeyAndKindAndEventDateBetween(Long farmerId, String cellKey, WeatherRisk.Kind kind,
                                                                LocalDate from, LocalDate to);

    /** WARNING → DANGER atomik: ikki instans bir vaqtda ko'rsa ham bittasigina 1 qaytaradi (xabar bir marta) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WeatherAlertEntity a SET a.level = uz.app.agro.weather.WeatherRisk.Level.DANGER, a.value = :value,
                   a.updatedDate = CURRENT_TIMESTAMP
             WHERE a.id = :id AND a.level = uz.app.agro.weather.WeatherRisk.Level.WARNING
            """)
    int escalate(@Param("id") Long id, @Param("value") BigDecimal value);
}
