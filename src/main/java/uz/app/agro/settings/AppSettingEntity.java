package uz.app.agro.settings;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Qiymat matn ("3", "true", "MONTHLY") — tur va tekshiruv Setting enum'ida
@Entity
@Table(name = "app_settings")
@Getter
@Setter
public class AppSettingEntity {

    @Id
    @Column(length = 64)
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "updated_by_email", length = 120)
    private String updatedByEmail;

    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate = LocalDateTime.now();
}
