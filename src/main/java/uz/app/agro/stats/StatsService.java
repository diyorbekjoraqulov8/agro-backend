package uz.app.agro.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.stats.StatsResponses.Overview;
import uz.app.agro.stats.StatsResponses.SourceFunnel;
import uz.app.agro.stats.StatsResponses.SourcesReport;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private static final LocalDate EPOCH = LocalDate.of(2026, 1, 1);   // loyihadan oldin ma'lumot yo'q

    private final StatsRepository statsRepository;

    @PreAuthorize(Permissions.CAN_READ_FARMER)
    public SourcesReport sources(LocalDate from, LocalDate to) {
        // null parametr native SQL'da tipini yo'qotadi (PostgreSQL "could not determine data type") — default shu yerda
        LocalDate f = from != null ? from : EPOCH;
        LocalDate t = to != null ? to : LocalDate.now();
        if (f.isAfter(t)) throw new BadRequestException("from to dan keyin bo'lmasin");
        if (f.getYear() < 2000 || t.getYear() > 2100) {
            throw new BadRequestException("Sana 2000–2100 yillar oralig'ida bo'lsin");
        }

        List<SourceFunnel> rows = statsRepository.bySource(f.atStartOfDay(), t.plusDays(1).atStartOfDay())
                .stream()
                .map(r -> new SourceFunnel(r.getSource(), r.getFarmers(), r.getWithField(), r.getAnalyzed(),
                        r.getLeads(), r.getFarmers() == 0 ? 0 : Math.round(r.getLeads() * 1000.0 / r.getFarmers()) / 10.0))
                .toList();
        return new SourcesReport(f, t, rows);
    }

    @PreAuthorize(Permissions.CAN_READ_FARMER)
    public Overview overview() {
        var t = statsRepository.totals();
        return new Overview(t.getFarmers(), t.getFields(), t.getTotalAreaHa(), t.getLeads(),
                toMap(statsRepository.analysesByStatus()),
                toMap(statsRepository.farmersByLeadStatus()),
                toMap(statsRepository.farmersByCountry()));
    }

    private static Map<String, Long> toMap(List<StatsRepository.CountRow> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        rows.forEach(r -> m.put(r.getKey(), r.getCount()));
        return m;
    }
}
