package uz.app.agro.onboarding.dto;

import uz.app.agro.onboarding.StepKind;
import uz.app.agro.onboarding.StepTarget;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record StepResponse(
        Long id, String code, int position, StepKind kind, StepTarget target, Map<String, String> texts,
        List<StepOption> options, boolean system, boolean active, String updatedByEmail, LocalDateTime updatedDate,
        long answers
) {}
