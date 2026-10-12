package uz.app.agro.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fermerning BUGUNGI tarifi va limitlari.
 * paid=false — to'lov yo'q, FREE tizim rejasi (tahlillar umrbod hisoblanadi);
 * paid=true — faol obuna (tahlillar obuna davri ichida hisoblanadi).
 */
public record EffectivePlan(
        String planCode,
        String planName,
        boolean paid,
        Long subscriptionId,
        LocalDate validUntil,
        BigDecimal maxFieldHa,
        int maxFields,
        Integer maxAnalyses,            // null — cheksiz
        long usedAnalyses
) {
    public boolean analysesLeft() {
        return maxAnalyses == null || usedAnalyses < maxAnalyses;
    }
}
