package uz.app.agro.field;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import uz.app.agro.common.BaseEntity;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.IrrigationType;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "fields")
@Getter
@Setter
@SQLDelete(sql = "UPDATE fields SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
public class FieldEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id", nullable = false)
    private FarmerEntity farmer;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Crop crop;

    // GeoJSON Polygon: {"type":"Polygon","coordinates":[[[lon,lat],...]]}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "boundary_geojson", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> boundaryGeojson;

    @Column(name = "area_ha", precision = 10, scale = 2)
    private BigDecimal areaHa;

    @Column(name = "centroid_lat", precision = 9, scale = 6)
    private BigDecimal centroidLat;

    @Column(name = "centroid_lon", precision = 9, scale = 6)
    private BigDecimal centroidLon;

    // Har dalaniki (boshlang'ich qiymat — fermerning onboarding javobi); null — so'ralmagan
    @Enumerated(EnumType.STRING)
    @Column(name = "irrigation_method", length = 32)
    private IrrigationType irrigationMethod;
}
