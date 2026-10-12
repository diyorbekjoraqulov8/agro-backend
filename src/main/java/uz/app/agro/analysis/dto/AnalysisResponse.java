package uz.app.agro.analysis.dto;

import uz.app.agro.analysis.AnalysisJob;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

// result — Python javobi (rasmlarsiz): zones, growing_season, stress_periods, water, timeseries,
// recommendations. Rasmlar: GET /analyses/{id}/map.png va /chart.png (faqat DONE bo'lsa)
public record AnalysisResponse(
        Long id,
        Long fieldId,
        AnalysisJob.Kind kind,
        AnalysisJob.Status status,
        LocalDate seasonStart,
        LocalDate seasonEnd,
        int attempts,
        LocalDateTime nextTryAt,
        String error,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdDate,
        Map<String, Object> result
) {}
