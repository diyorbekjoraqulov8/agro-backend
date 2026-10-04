package uz.app.agro.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.app.agro.common.PageResponse;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) AuditEvent eventType,
            @RequestParam(required = false) Long actorId,
            @PageableDefault(size = 50, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return auditQueryService.search(eventType, actorId, pageable);
    }
}