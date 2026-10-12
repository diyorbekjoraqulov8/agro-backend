package uz.app.agro.field.dto;

import uz.app.agro.field.Crop;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Ro'yxat uchun — GeoJSON'siz (poligon 200 nuqtagacha, ro'yxatda keraksiz og'irlik)
public record FieldSummary(
        Long id,
        Long farmerId,
        String name,
        Crop crop,
        BigDecimal areaHa,
        BigDecimal centroidLat,
        BigDecimal centroidLon,
        LocalDateTime createdDate
) {}
