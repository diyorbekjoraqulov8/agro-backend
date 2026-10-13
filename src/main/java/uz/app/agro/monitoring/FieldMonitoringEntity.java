package uz.app.agro.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Monitoring navbati: dala qachon tekshiriladi va oxirgi ko'rilgan surat. Qator job'da avtomatik paydo bo'ladi */
@Getter
@Setter
@Entity
@Table(name = "field_monitoring")
public class FieldMonitoringEntity {

    @Id
    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "last_scene_date")
    private LocalDate lastSceneDate;

    /** Metadata bo'yicha ko'rilgan eng yangi surat (bulutli bo'lsa ham) — keyingi tekshiruvda shundan keyingisi so'raladi */
    @Column(name = "last_seen_date")
    private LocalDate lastSeenDate;

    @Column(name = "last_ndvi", precision = 5, scale = 3)
    private BigDecimal lastNdvi;

    @Column(name = "next_check_at", nullable = false)
    private LocalDateTime nextCheckAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(nullable = false)
    private int failures;
}
