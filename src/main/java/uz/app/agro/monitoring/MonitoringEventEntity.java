package uz.app.agro.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Har yangi surat natijasi (tarix). Fermerga xabar — faqat verdict = DECLINE */
@Getter
@Setter
@Entity
@Table(name = "monitoring_events")
public class MonitoringEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "scene_date", nullable = false)
    private LocalDate sceneDate;

    @Column(name = "ref_date")
    private LocalDate refDate;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal ndvi;

    @Column(name = "ndvi_ref", precision = 5, scale = 3)
    private BigDecimal ndviRef;

    @Column(name = "decline_pct", precision = 5, scale = 1)
    private BigDecimal declinePct;

    @Column(name = "decline_side", length = 8)
    private String declineSide;

    @Column(name = "improve_pct", precision = 5, scale = 1)
    private BigDecimal improvePct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Verdict verdict;

    @Column(name = "map_file_hash_id", length = 22)
    private String mapFileHashId;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
}
