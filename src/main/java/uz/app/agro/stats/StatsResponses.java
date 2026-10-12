package uz.app.agro.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class StatsResponses {

    private StatsResponses() {}

    public record SourceFunnel(String source, long farmers, long withField, long analyzed, long leads,
                               double leadRatePct) {}

    public record SourcesReport(LocalDate from, LocalDate to, List<SourceFunnel> sources) {}

    public record Overview(long farmers, long fields, BigDecimal totalAreaHa, long leads,
                           Map<String, Long> analysesByStatus,
                           Map<String, Long> farmersByLeadStatus,
                           Map<String, Long> farmersByCountry) {}
}
