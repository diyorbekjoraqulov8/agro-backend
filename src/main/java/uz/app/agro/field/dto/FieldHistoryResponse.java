package uz.app.agro.field.dto;

import uz.app.agro.field.Crop;
import uz.app.agro.field.FieldSeasonEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * L1b: dala holati tarixi — tizim nimani ko'rgan va nima degan. Admin aniqlikni shu yerda tekshiradi.
 * Davr default'da joriy + oldingi mavsum; seasons — tanlagich uchun.
 */
public record FieldHistoryResponse(
        LocalDate from,
        LocalDate to,
        List<Season> seasons,
        List<NdviPoint> ndvi,
        List<Event> events
) {

    public record Season(Long id, Crop crop, FieldSeasonEntity.Sowing sowing, LocalDate sownOn,
                         LocalDate startedOn, LocalDate finishedOn, FieldSeasonEntity.Status status) {}

    /** source: ANALYSIS (refId — tahlil, eng yangi tahlil ustun) | MONITORING (refId — hodisa) */
    public record NdviPoint(LocalDate date, double ndvi, Double ndmi, String source, Long refId) {}

    /**
     * type: ANALYSIS | MONITORING | WEATHER | NOTIFICATION | OBSERVATION (L2 kuzatuv, status — holat). status — turga xos (DONE/FAILED, verdict, level,
     * SENT/SKIPPED…), data — ko'rsatish uchun qiymatlar (matn admin'da yasaladi)
     */
    public record Event(LocalDateTime at, String type, String status, Long refId, Map<String, Object> data) {}
}
