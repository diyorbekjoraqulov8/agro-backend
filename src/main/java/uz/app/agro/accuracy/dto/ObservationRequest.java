package uz.app.agro.accuracy.dto;

import jakarta.validation.constraints.*;
import uz.app.agro.accuracy.ObservationEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** lat/lon — ikkalasi yoki hech biri (butun dala); nuqta dala chegarasi ichida bo'lishi service'da tekshiriladi */
public record ObservationRequest(
        @NotNull @PastOrPresent LocalDate observedOn,
        @NotNull ObservationEntity.Condition condition,
        ObservationEntity.Cause cause,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal lon,
        @Size(max = 1000) String note
) {}
