package uz.app.agro.field.dto;

import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.field.Crop;
import uz.app.agro.field.FieldSeasonEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record FieldResponse(
        Long id,
        Long farmerId,
        String farmerName,
        boolean lab,                            // Lab dalasi (admin chizgan) — fermerniki emas
        String name,
        Crop crop,
        BigDecimal areaHa,
        BigDecimal centroidLat,
        BigDecimal centroidLon,
        Map<String, Object> boundaryGeojson,   // GeoJSON Polygon — panel xaritada chizadi
        IrrigationType irrigationMethod,
        Season season,                          // faol mavsum; null — hali yo'q
        LocalDateTime createdDate
) {
    public record Season(Crop crop, FieldSeasonEntity.Sowing sowing, LocalDate sownOn, boolean sownOnApprox) {}
}
