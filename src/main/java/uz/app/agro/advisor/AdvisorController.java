package uz.app.agro.advisor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.advisor.dto.AdvisorItem;
import uz.app.agro.advisor.dto.AdvisorStats;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;

import java.util.Set;

@Tag(name = "AI maslahat")
@RestController
@RequestMapping("/advisor")
@RequiredArgsConstructor
public class AdvisorController {

    private static final Set<String> SORTABLE = Set.of("id", "createdDate", "answeredAt");

    private final AdvisorAdminService service;

    @Operation(summary = "So'rovlar", description = "Huquq: `advisor:read`. filter: ALL | PENDING | ANSWERED | FAILED")
    @GetMapping("/requests")
    public PageResponse<AdvisorItem> list(@RequestParam(defaultValue = "ALL") AdvisorAdminService.Filter filter,
                                          @RequestParam(required = false) Long farmerId,
                                          @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(filter, farmerId, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "So'rov", description = "Huquq: `advisor:read`")
    @GetMapping("/requests/{id}")
    public AdvisorItem get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Statistika (30 kun)", description = "Huquq: `advisor:read`")
    @GetMapping("/stats")
    public AdvisorStats stats() {
        return service.stats();
    }
}
