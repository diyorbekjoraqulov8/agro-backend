package uz.app.agro.advisor.dto;

import uz.app.agro.advisor.AdvisorRequestEntity;

import java.time.LocalDateTime;
import java.util.Map;

/** Admin: so'rov — rasm va AI tavsiyasi (diagnosis — AdvisorDiagnosis JSON, fermer tilida) */
public record AdvisorItem(
        Long id, Long farmerId, String farmerName, Long fieldId, String fieldName, String photoHashId, String farmerNote,
        String language, AdvisorRequestEntity.Status status, int attempts, String aiModel, Map<String, Object> diagnosis,
        String aiError, Integer inputTokens, Integer outputTokens, LocalDateTime createdDate, LocalDateTime answeredAt
) {}
