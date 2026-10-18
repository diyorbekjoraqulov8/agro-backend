package uz.app.agro.monitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** R4 "📸 Bugungi rasm" keshi (V32). Faqat qo'shiladi — BaseEntity/soft delete kerak emas */
@Getter
@Setter
@Entity
@Table(name = "field_snapshots")
public class FieldSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "scene_date", nullable = false)
    private LocalDate sceneDate;

    @Column(name = "file_hash_id", nullable = false, length = 32)
    private String fileHashId;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal ndvi;

    @Column(name = "weak_pct", nullable = false, precision = 5, scale = 1)
    private BigDecimal weakPct;

    @Column(name = "weak_lat", precision = 9, scale = 6)
    private BigDecimal weakLat;

    @Column(name = "weak_lon", precision = 9, scale = 6)
    private BigDecimal weakLon;

    @Column(name = "telegram_file_id")
    private String telegramFileId;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
}
