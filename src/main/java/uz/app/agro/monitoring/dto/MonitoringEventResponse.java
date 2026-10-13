package uz.app.agro.monitoring.dto;

import uz.app.agro.monitoring.Verdict;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MonitoringEventResponse(Long id, LocalDate sceneDate, LocalDate refDate, BigDecimal ndvi, BigDecimal ndviRef,
                                      BigDecimal declinePct, String declineSide, BigDecimal improvePct, Verdict verdict,
                                      String mapFileHashId, LocalDateTime createdDate) {}
