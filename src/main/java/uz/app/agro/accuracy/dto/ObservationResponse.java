package uz.app.agro.accuracy.dto;

import uz.app.agro.accuracy.ObservationEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ObservationResponse(Long id, Long fieldId, LocalDate observedOn, ObservationEntity.Condition condition,
                                  ObservationEntity.Cause cause, BigDecimal lat, BigDecimal lon, String note,
                                  String authorEmail, LocalDateTime createdDate) {

    public static ObservationResponse of(ObservationEntity o) {
        return new ObservationResponse(o.getId(), o.getFieldId(), o.getObservedOn(), o.getCondition(), o.getCause(),
                o.getLat(), o.getLon(), o.getNote(), o.getAuthorEmail(), o.getCreatedDate());
    }
}
