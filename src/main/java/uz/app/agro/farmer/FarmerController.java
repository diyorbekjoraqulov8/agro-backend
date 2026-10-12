package uz.app.agro.farmer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;
import uz.app.agro.farmer.dto.FarmerDetail;
import uz.app.agro.farmer.dto.FarmerListItem;
import uz.app.agro.farmer.dto.LeadUpdateRequest;
import uz.app.agro.field.Crop;

import java.util.Set;

@Tag(name = "Fermerlar", description = "Botdan kelgan fermerlar va lead'lar")
@RestController
@RequestMapping("/farmers")
@RequiredArgsConstructor
public class FarmerController {

    private static final Set<String> SORTABLE = Set.of("createdDate", "id", "fullName", "leadStatus", "countryCode", "source");

    private final FarmerService farmerService;

    @Operation(summary = "Fermerlar ro'yxati",
               description = "Huquq: `farmer:read`. `q` — ism, username yoki telefon bo'yicha qidiruv")
    @GetMapping
    public PageResponse<FarmerListItem> search(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) LeadStatus leadStatus,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Crop crop,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 50, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return farmerService.search(country, leadStatus, source, crop, q, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Fermer va uning dalalari", description = "Huquq: `farmer:read`")
    @GetMapping("/{id}")
    public FarmerDetail get(@PathVariable Long id) {
        return farmerService.get(id);
    }

    @Operation(summary = "Lead holatini o'zgartirish", description = "Huquq: `farmer:update`. Audit: `LEAD_STATUS_CHANGED`")
    @PatchMapping("/{id}/lead")
    public FarmerDetail updateLead(@PathVariable Long id, @Valid @RequestBody LeadUpdateRequest body) {
        return farmerService.updateLead(id, body);
    }
}
