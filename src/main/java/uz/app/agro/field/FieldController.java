package uz.app.agro.field;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;
import uz.app.agro.field.dto.CreateLabFieldRequest;
import uz.app.agro.field.dto.FieldHistoryResponse;
import uz.app.agro.field.dto.FieldResponse;
import uz.app.agro.field.dto.FieldSummary;

import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Dalalar", description = "Fermerlar Mini App'da chizgan dalalar")
@RestController
@RequestMapping("/fields")
@RequiredArgsConstructor
public class FieldController {

    private static final Set<String> SORTABLE = Set.of("createdDate", "id", "name", "areaHa", "crop");

    private final FieldService fieldService;
    private final LabFieldService labFieldService;
    private final FieldHistoryService historyService;

    @Operation(summary = "Dala holati tarixi", description = "Huquq: `field:read` va `analysis:read`. NDVI (tahlil + monitoring) "
            + "va hodisalar: tahlillar, monitoring, ob-havo, xabarlar. Davr berilmasa — joriy + oldingi mavsum; ko'pi bilan 3 yil")
    @GetMapping("/{id}/history")
    public FieldHistoryResponse history(@PathVariable Long id,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return historyService.history(id, from, to);
    }

    @Operation(summary = "Dalalar ro'yxati (GeoJSON'siz)", description = "Huquq: `field:read`")
    @GetMapping
    public PageResponse<FieldSummary> search(
            @RequestParam(required = false) Long farmerId,
            @RequestParam(required = false) Crop crop,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Boolean lab,
            @ParameterObject @PageableDefault(size = 50, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return fieldService.search(farmerId, crop, country, lab, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Dala va uning chegarasi (GeoJSON)", description = "Huquq: `field:read`")
    @GetMapping("/{id}")
    public FieldResponse get(@PathVariable Long id) {
        return fieldService.get(id);
    }

    @Operation(summary = "Lab dala qo'shish (admin chizadi)", description = "Huquq: `field:write`. Birinchi tahlil navbatga qo'yiladi")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FieldResponse create(@Valid @RequestBody CreateLabFieldRequest req) {
        return labFieldService.create(req);
    }

    @Operation(summary = "Lab dalani o'chirish", description = "Huquq: `field:write`. Fermer dalasi — 409")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        labFieldService.delete(id);
    }
}
