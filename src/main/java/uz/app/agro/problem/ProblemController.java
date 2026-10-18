package uz.app.agro.problem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;
import uz.app.agro.problem.dto.ProblemItem;
import uz.app.agro.problem.dto.ProblemSummary;

import java.util.Map;
import java.util.Set;

@Tag(name = "Muammolar")
@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemController {

    private static final Set<String> SORTABLE = Set.of("id", "lastSeen", "firstSeen", "occurrences");

    private final ProblemAdminService service;

    @Operation(summary = "Muammolar", description = "Huquq: `problems:read`. state: OPEN | RESOLVED | ALL")
    @GetMapping
    public PageResponse<ProblemItem> list(@RequestParam(defaultValue = "OPEN") ProblemAdminService.State state,
                                          @RequestParam(required = false) ProblemSource source,
                                          @RequestParam(required = false) ProblemEntity.Level level,
                                          @RequestParam(required = false) Long farmerId,
                                          @ParameterObject @PageableDefault(size = 30, sort = "lastSeen", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(state, source, level, farmerId, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Muammo (stack trace bilan)", description = "Huquq: `problems:read`")
    @GetMapping("/{id}")
    public ProblemItem get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Xizmatlar holati va manbalar bo'yicha sanoq", description = "Huquq: `problems:read`")
    @GetMapping("/summary")
    public ProblemSummary summary() {
        return service.summary();
    }

    @Operation(summary = "Ochiq xatolar soni (24 soat) — menyu nishoni", description = "Huquq: `problems:read`")
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("errors", service.recentErrors());
    }

    @Operation(summary = "Hal qilindi", description = "Huquq: `problems:manage`")
    @PostMapping("/{id}/resolve")
    public ProblemItem resolve(@PathVariable Long id) {
        return service.resolve(id);
    }

    @Operation(summary = "Manbaning barcha ochiq muammolari — hal qilindi", description = "Huquq: `problems:manage`")
    @PostMapping("/resolve-all")
    public Map<String, Integer> resolveAll(@RequestParam ProblemSource source) {
        return Map.of("resolved", service.resolveAll(source));
    }
}
