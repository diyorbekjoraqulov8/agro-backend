package uz.app.agro.analysis.dto;

import uz.app.agro.analysis.AnalysisJob;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnalysisListItem(
        Long id,
        Long fieldId,
        AnalysisJob.Kind kind,
        AnalysisJob.Status status,
        LocalDate seasonStart,
        LocalDate seasonEnd,
        int attempts,
        String error,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdDate
) {}
