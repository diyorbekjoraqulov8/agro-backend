package uz.app.agro.billing.dto;

import uz.app.agro.billing.DiscountAudience;
import uz.app.agro.billing.DiscountEntity;
import uz.app.agro.billing.DiscountKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record DiscountResponse(
        Long id, String name, DiscountKind kind, DiscountEntity.ValueType valueType, BigDecimal value,
        Set<DiscountAudience> audiences, Set<Long> planIds, LocalDate startsOn, LocalDate endsOn, boolean active,
        boolean activeToday, long usedCount, LocalDateTime updatedDate, String updatedByEmail
) {}
