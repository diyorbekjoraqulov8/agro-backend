package uz.app.agro.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlanResponse(
        Long id, String code, String name, String description,
        BigDecimal price, String currency, Integer durationDays,
        BigDecimal maxFieldHa, BigDecimal maxTotalHa, Integer maxFields, Integer maxAnalyses, boolean contactSales,
        boolean systemPlan, boolean active, LocalDateTime updatedDate
) {}
