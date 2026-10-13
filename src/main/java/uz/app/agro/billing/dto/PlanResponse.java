package uz.app.agro.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlanResponse(
        Long id, String code, String name, String description,
        BigDecimal price, String currency, Integer durationDays,
        BigDecimal maxFieldHa, Integer maxFields, Integer maxAnalyses,
        boolean systemPlan, boolean active, LocalDateTime updatedDate
) {}
