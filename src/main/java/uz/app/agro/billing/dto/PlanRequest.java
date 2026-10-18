package uz.app.agro.billing.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// code faqat yaratishda; tizim rejasida (FREE) narx/muddat e'tiborsiz qoldiriladi
public record PlanRequest(
        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}", message = "code: katta lotin harf, raqam, _ (masalan YEARLY)")
        String code,

        @NotBlank(message = "name ko'rsatilishi shart") @Size(max = 80, message = "name ko'pi bilan 80 belgi")
        String name,

        @Size(max = 1000, message = "description ko'pi bilan 1000 belgi")
        String description,

        @NotNull(message = "price ko'rsatilishi shart") @DecimalMin(value = "0", message = "price manfiy bo'lmasin")
        @Digits(integer = 10, fraction = 2, message = "price: 10 xona + 2 kasr")
        BigDecimal price,

        @NotNull(message = "currency ko'rsatilishi shart") @Pattern(regexp = "[A-Z]{3}", message = "currency: ISO 4217 (UZS, USD)")
        String currency,

        @Min(value = 1, message = "durationDays kamida 1") @Max(value = 3660, message = "durationDays ko'pi bilan 3660")
        Integer durationDays,

        // 500 — Python satellite-service'ning texnik chegarasi (geometry.py MAX_HA)
        @NotNull(message = "maxFieldHa ko'rsatilishi shart")
        @DecimalMin(value = "0.5", message = "maxFieldHa kamida 0.5") @DecimalMax(value = "500", message = "maxFieldHa ko'pi bilan 500 (tahlil xizmati chegarasi)")
        BigDecimal maxFieldHa,

        // Barcha dalalar jami maydoni; null — cheksiz
        @DecimalMin(value = "0.5", message = "maxTotalHa kamida 0.5") @DecimalMax(value = "1000000", message = "maxTotalHa ko'pi bilan 1 000 000")
        @Digits(integer = 8, fraction = 2, message = "maxTotalHa: 8 xona + 2 kasr")
        BigDecimal maxTotalHa,

        // null — cheksiz
        @Min(value = 1, message = "maxFields kamida 1") @Max(value = 1000, message = "maxFields ko'pi bilan 1000")
        Integer maxFields,

        @Min(value = 0, message = "maxAnalyses manfiy bo'lmasin")
        Integer maxAnalyses,

        boolean active,

        // Narx o'rniga "ariza qoldirish" (Agro Biznes). Tizim rejasida e'tiborsiz
        boolean contactSales
) {}
