package uz.app.agro.weather;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Yuborilgan ogohlantirish — takrorni oldini olish va kuchayishni aniqlash uchun (fermer + katak + tur + sana — UNIQUE) */
@Getter
@Setter
@Entity
@Table(name = "weather_alerts")
public class WeatherAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false)
    private Long farmerId;

    @Column(name = "cell_key", nullable = false, length = 24)
    private String cellKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WeatherRisk.Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WeatherRisk.Level level;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false, precision = 6, scale = 1)
    private BigDecimal value;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
