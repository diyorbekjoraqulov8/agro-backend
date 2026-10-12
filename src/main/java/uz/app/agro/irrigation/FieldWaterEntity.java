package uz.app.agro.irrigation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_water")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class FieldWaterEntity {

    public enum ReminderKind { DUE, SOON, STATUS }

    @Id
    @Column(name = "field_id")
    private Long fieldId;

    @Enumerated(EnumType.STRING)
    @Column(name = "soil_type", length = 8)
    private SoilType soilType;

    @Enumerated(EnumType.STRING)
    @Column(name = "soil_type_auto", length = 8)
    private SoilType soilTypeAuto;

    @Column(name = "sand_pct", precision = 5, scale = 1)
    private BigDecimal sandPct;

    @Column(name = "clay_pct", precision = 5, scale = 1)
    private BigDecimal clayPct;

    @Column(name = "last_irrigated_on")
    private LocalDate lastIrrigatedOn;

    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    @Column(precision = 4, scale = 3)
    private BigDecimal ndvi;

    @Column(name = "ndvi_on")
    private LocalDate ndviOn;

    @Column(length = 64)
    private String timezone;

    @Column(name = "utc_offset_seconds")
    private Integer utcOffsetSeconds;

    @Column(name = "next_irrigation_on")
    private LocalDate nextIrrigationOn;

    @Column(name = "depletion_pct", precision = 6, scale = 1)
    private BigDecimal depletionPct;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Column(name = "last_reminder_on")
    private LocalDate lastReminderOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_reminder_kind", length = 16)
    private ReminderKind lastReminderKind;

    /** Job shu mahalliy kunda qaror qilgan (yuborilgan yoki yuboradigan narsa yo'q) */
    @Column(name = "last_checked_on")
    private LocalDate lastCheckedOn;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    /** Fermer javobi ustun; "bilmayman" — SoilGrids; u ham yo'q — o'rtacha */
    public SoilType effectiveSoil() {
        return soilType != null ? soilType : soilTypeAuto != null ? soilTypeAuto : SoilType.LOAM;
    }

    /** Dala mahalliy sanasi (Open-Meteo bergan offset; hali yo'q — server vaqti) */
    public LocalDate localToday() {
        return utcOffsetSeconds == null ? LocalDate.now()
                : java.time.OffsetDateTime.now(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds)).toLocalDate();
    }
}
