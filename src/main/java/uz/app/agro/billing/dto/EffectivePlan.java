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
        BigDecimal maxTotalHa,          // null — cheksiz (barcha dalalar jami)
        Integer maxFields,              // null — cheksiz
        Integer maxAnalyses,            // null — cheksiz
        long usedAnalyses
) {
    public boolean fieldsLeft(long fields) {
        return maxFields == null || fields < maxFields;
    }

    /**
     * Yangi (yoki tahrirlanayotgan) dala ko'pi bilan necha gektar bo'la oladi: bitta dala chegarasi va jami qoldiq.
     * @param usedHa boshqa dalalar jami maydoni (tahrirlanayotgan dala o'zi hisoblanmaydi)
     */
    public BigDecimal allowedHa(BigDecimal usedHa) {
        if (maxTotalHa == null) return maxFieldHa;
        BigDecimal left = maxTotalHa.subtract(usedHa).max(BigDecimal.ZERO);
        return left.min(maxFieldHa);
    }

    /** Cheksiz maydonli pullik tarif (Agro Biznes) — unga pastroq tarif (mavsumiy ham) taklif qilinmaydi */
    public boolean unlimited() {
        return paid && maxTotalHa == null;
    }

    public boolean analysesLeft() {
        return maxAnalyses == null || usedAnalyses < maxAnalyses;
    }
}
