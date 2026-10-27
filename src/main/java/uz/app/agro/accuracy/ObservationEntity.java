package uz.app.agro.accuracy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import uz.app.agro.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** L2 ground truth: admin dalada ko'rgani. lat/lon null — butun dala */
@Entity
@Table(name = "field_observations")
@SQLDelete(sql = "UPDATE field_observations SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
public class ObservationEntity extends BaseEntity {

    public enum Condition { GOOD, WEAK, DAMAGED, BARE, HARVESTED }

    public enum Cause { WATER, NUTRIENT, DISEASE, PEST, WEEDS, SALINITY, LODGING, HAIL, OTHER, UNKNOWN }

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Condition condition;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Cause cause;

    @Column(precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(precision = 9, scale = 6)
    private BigDecimal lon;

    @Column(length = 1000)
    private String note;

    @Column(name = "author_email")
    private String authorEmail;

    public boolean hasPoint() {
        return lat != null && lon != null;
    }
}
