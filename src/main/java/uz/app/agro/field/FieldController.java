package uz.app.agro.field;

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
import uz.app.agro.field.dto.FieldResponse;
import uz.app.agro.field.dto.FieldSummary;

import java.util.Set;

@Tag(name = "Dalalar", description = "Fermerlar Mini App'da chizgan dalalar")
@RestController
@RequestMapping("/fields")
@RequiredArgsConstructor
public class FieldController {

    private static final Set<String> SORTABLE = Set.of("createdDate", "id", "name", "areaHa", "crop");

    private final FieldService fieldService;

    @Operation(summary = "Dalalar ro'yxati (GeoJSON'siz)", description = "Huquq: `field:read`")
    @GetMapping
    public PageResponse<FieldSummary> search(
            @RequestParam(required = false) Long farmerId,
            @RequestParam(required = false) Crop crop,
            @RequestParam(required = false) String country,
            @ParameterObject @PageableDefault(size = 50, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return fieldService.search(farmerId, crop, country, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Dala va uning chegarasi (GeoJSON)", description = "Huquq: `field:read`")
    @GetMapping("/{id}")
    public FieldResponse get(@PathVariable Long id) {
        return fieldService.get(id);
    }
}
