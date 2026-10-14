package uz.app.agro.advisor.dto;

import java.util.List;
import java.util.Map;

/** So'nggi 30 kun: holat va AI xulosasi turlari bo'yicha; tokenlar model bo'yicha (narx — admin panelda taxminan) */
public record AdvisorStats(Map<String, Long> byStatus, Map<String, Long> byCategory, List<ModelTokens> tokens) {

    public record ModelTokens(String model, long inputTokens, long outputTokens, long requests) {}
}
