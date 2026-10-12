package uz.app.agro.field.dto;

import uz.app.agro.field.Crop;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record FieldResponse(
        Long id,
        Long farmerId,
        String farmerName,
        String name,
        Crop crop,
        BigDecimal areaHa,
        BigDecimal centroidLat,
        BigDecimal centroidLon,
        Map<String, Object> boundaryGeojson,   // GeoJSON Polygon — panel xaritada chizadi
        LocalDateTime createdDate
) {}
