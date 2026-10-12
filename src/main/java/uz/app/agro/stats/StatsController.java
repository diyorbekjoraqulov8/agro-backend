package uz.app.agro.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.app.agro.stats.StatsResponses.Overview;
import uz.app.agro.stats.StatsResponses.SourcesReport;

import java.time.LocalDate;

@Tag(name = "Statistika", description = "Admin panel dashboard'i")
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "Umumiy ko'rsatkichlar", description = "Huquq: `farmer:read`")
    @GetMapping("/overview")
    public Overview overview() {
        return statsService.overview();
    }

    @Operation(summary = "Reklama kanallari voronkasi",
               description = "Huquq: `farmer:read`. Fermer kelgan sana bo'yicha (`from`/`to` — YYYY-MM-DD, ikkalasi ham kiradi)")
    @GetMapping("/sources")
    public SourcesReport sources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.sources(from, to);
    }
}
