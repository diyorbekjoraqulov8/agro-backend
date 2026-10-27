package uz.app.agro.accuracy.dto;

import uz.app.agro.accuracy.Match;

import java.util.List;
import java.util.Map;

/**
 * L2 aniqlik hisoboti.
 * manual — xodim baholari: xulosa turi (ANALYSIS/MONITORING) → baho (CORRECT/PARTIAL/WRONG) → soni.
 * auto — kuzatuvni tizim xulosasi bilan avtomatik solishtirish: xulosa turi → natija (Match) → soni.
 */
public record AccuracyReport(
        Map<String, Map<String, Long>> manual,
        Map<String, Map<Match.Result, Long>> auto,
        List<Row> rows,
        boolean truncated                                   // kuzatuvlar ObservationRepository.REPORT_LIMIT dan ko'p — eng yangilari
) {

    /** Har kuzatuv: tahlil va monitoring bilan solishtirish natijasi (sababi — reason, admin'da matnga o'giriladi) */
    public record Row(ObservationResponse observation, String fieldName, boolean lab,
                      Match analysis, Match monitoring) {}
}
