package uz.app.agro.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.app.agro.billing.DiscountAudience;
import uz.app.agro.billing.DiscountEntity;
import uz.app.agro.billing.DiscountKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Foiz — 1..90 (servisda), qat'iy — so'm (tarif valyutasida). planIds bo'sh — barcha narxli tariflar */
public record DiscountRequest(
        @NotBlank(message = "name ko'rsatilishi shart") @Size(max = 100, message = "name ko'pi bilan 100 belgi") String name,
        @NotNull(message = "kind ko'rsatilishi shart") DiscountKind kind,
        @NotNull(message = "valueType ko'rsatilishi shart") DiscountEntity.ValueType valueType,
        @NotNull(message = "value ko'rsatilishi shart") @DecimalMin(value = "0.01", message = "value musbat bo'lsin")
        @Digits(integer = 10, fraction = 2, message = "value: 10 xona + 2 kasr") BigDecimal value,
        @NotEmpty(message = "Kamida bitta fermer guruhini tanlang") Set<DiscountAudience> audiences,
        @Size(max = 30, message = "Ko'pi bilan 30 ta tarif") Set<@NotNull(message = "planIds ichida bo'sh qiymat") Long> planIds,
        @NotNull(message = "startsOn ko'rsatilishi shart") LocalDate startsOn,
        LocalDate endsOn,
        boolean active
) {}
