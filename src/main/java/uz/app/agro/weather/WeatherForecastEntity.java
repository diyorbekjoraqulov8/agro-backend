package uz.app.agro.weather;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Katak prognozi (kesh): job har soat eskirganini yangilaydi; bot "🌦 Ob-havo" shu yerdan o'qiydi */
@Getter
@Setter
@Entity
@Table(name = "weather_forecasts")
public class WeatherForecastEntity {

    @Id
    @Column(name = "cell_key", length = 24)
    private String cellKey;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal lat;

    @Column(nullable = false, precision = 7, scale = 3)
    private BigDecimal lon;

    @Column(length = 64)
    private String timezone;

    @Column(name = "utc_offset_seconds")
    private Integer utcOffsetSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> days;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    public List<DailyForecast> forecast() {
        return days.stream().map(DailyForecast::fromMap).toList();
    }
}
