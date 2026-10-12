package uz.app.agro.satellite.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.Map;

// Python bilan shartnoma (satellite-service/app/schemas.py) — o'zgartirsangiz ikkala tomonda.
// snake_case faqat shu DTO'larda: global sozlansa skeletning camelCase API'lari buzilardi
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalyzeRequest(
        long fieldId,
        Map<String, Object> geometry,
        String crop,
        LocalDate seasonStart,
        LocalDate seasonEnd,
        boolean comparePreviousYear
) {}
