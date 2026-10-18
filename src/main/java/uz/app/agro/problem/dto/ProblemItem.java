package uz.app.agro.problem.dto;

import uz.app.agro.problem.ProblemEntity;
import uz.app.agro.problem.ProblemSource;

import java.time.LocalDateTime;

public record ProblemItem(
        Long id,
        ProblemSource source,
        ProblemEntity.Level level,
        Long farmerId,
        String farmerName,
        Long fieldId,
        String fieldName,
        String refType,
        Long refId,
        String message,
        String detail,
        int occurrences,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        LocalDateTime resolvedAt,
        String resolvedByEmail
) {}
