package uz.app.agro.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fermer uchun bir davr narxi: chegirma bo'lsa — finalPrice < basePrice va qaysi chegirma (eng foydalisi) */
public record PlanPrice(Long planId, BigDecimal basePrice, BigDecimal finalPrice, String currency,
                        Long discountId, String discountName, String discountLabel, LocalDate discountEndsOn) {
    public boolean discounted() {
        return discountId != null;
    }
}
