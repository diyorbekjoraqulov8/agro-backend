package uz.app.agro.accuracy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.accuracy.dto.*;

@Tag(name = "Aniqlik (L2)", description = "Ground truth kuzatuvlari, tizim xulosalariga baho, aniqlik hisoboti")
@RestController
@RequiredArgsConstructor
public class AccuracyController {

    private final AccuracyService service;

    @Operation(summary = "Kuzatuv qo'shish", description = "Huquq: `field:write`. lat/lon yo'q — butun dala; nuqta dala ichida bo'lishi shart")
    @PostMapping("/fields/{fieldId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationResponse create(@PathVariable Long fieldId, @Valid @RequestBody ObservationRequest req) {
        return service.create(fieldId, req);
    }

    @Operation(summary = "Kuzatuvni tahrirlash", description = "Huquq: `field:write`")
    @PutMapping("/observations/{id}")
    public ObservationResponse update(@PathVariable Long id, @Valid @RequestBody ObservationRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Kuzatuvni o'chirish", description = "Huquq: `field:write`. Soft delete")
    @DeleteMapping("/observations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @Operation(summary = "Xulosaga baho", description = "Huquq: `field:write`. type: ANALYSIS (faqat DONE) | MONITORING. Qayta yuborilsa — ustiga yoziladi")
    @PutMapping("/findings/{type}/{id}/review")
    public ReviewResponse review(@PathVariable FindingReviewEntity.TargetType type, @PathVariable Long id,
                                 @Valid @RequestBody ReviewRequest req) {
        return service.review(type, id, req);
    }

    @Operation(summary = "Bahoni olib tashlash", description = "Huquq: `field:write`")
    @DeleteMapping("/findings/{type}/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReview(@PathVariable FindingReviewEntity.TargetType type, @PathVariable Long id) {
        service.removeReview(type, id);
    }

    @Operation(summary = "Aniqlik hisoboti", description = "Huquq: `field:read` va `analysis:read`. lab: true — faqat Lab, false — faqat real, yo'q — hammasi")
    @GetMapping("/accuracy")
    public AccuracyReport report(@RequestParam(required = false) Boolean lab) {
        return service.report(lab);
    }
}
