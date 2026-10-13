package uz.app.agro.monitoring.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Dala sahifasi: navbat holati (null — hali kuzatilmagan: tarif yoki o'chirilgan) va so'nggi 20 surat natijasi */
public record FieldMonitoringResponse(Status status, List<MonitoringEventResponse> events) {

    public record Status(LocalDate lastSceneDate, BigDecimal lastNdvi, LocalDateTime nextCheckAt, LocalDateTime lastCheckedAt,
                        int failures) {}
}
