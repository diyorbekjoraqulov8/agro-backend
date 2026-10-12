package uz.app.agro.analysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.analysis.dto.AnalysisListItem;
import uz.app.agro.analysis.dto.AnalysisResponse;
import uz.app.agro.analysis.dto.RunAnalysisRequest;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;

import java.util.Set;
import java.time.Duration;

@Tag(name = "Tahlillar", description = "Sun'iy yo'ldosh tahlili (Python satellite-service orqali, navbat bilan)")
@RestController
@RequiredArgsConstructor
public class AnalysisController {

    private static final Set<String> SORTABLE = Set.of("createdDate", "id", "status", "finishedAt");

    private final AnalysisService analysisService;

    @Operation(summary = "Tahlillar ro'yxati (natija va rasmlarsiz)", description = "Huquq: `analysis:read`")
    @GetMapping("/analyses")
    public PageResponse<AnalysisListItem> search(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) AnalysisJob.Status status,
            @RequestParam(required = false) AnalysisJob.Kind kind,
            @ParameterObject @PageableDefault(size = 50, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return analysisService.search(fieldId, status, kind, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Tahlil natijasi", description = "Huquq: `analysis:read`")
    @GetMapping("/analyses/{id}")
    public AnalysisResponse get(@PathVariable Long id) {
        return analysisService.get(id);
    }

    @Operation(summary = "Zonalar xaritasi (PNG)", description = "Huquq: `analysis:read`. Faqat DONE tahlilda")
    // produces YO'Q: 404 da ProblemDetail (JSON) qaytadi — produces=image/png bo'lsa Spring uni
    // yozolmay 406/500 beradi. Content-Type'ni ResponseEntity o'zi qo'yadi
    @GetMapping("/analyses/{id}/map.png")
    public ResponseEntity<byte[]> map(@PathVariable Long id) {
        return png(analysisService.image(id, true));
    }

    @Operation(summary = "Mavsum grafigi (PNG)", description = "Huquq: `analysis:read`. Faqat DONE tahlilda")
    @GetMapping("/analyses/{id}/chart.png")
    public ResponseEntity<byte[]> chart(@PathVariable Long id) {
        return png(analysisService.image(id, false));
    }

    @Operation(summary = "Dalani tahlilga yuborish",
               description = "Huquq: `analysis:run`. Navbatga qo'yadi (202), worker 1-2 daqiqada bajaradi. "
                       + "Tana bo'sh — oxirgi 12 oy. Dalada faol tahlil bo'lsa — 409")
    @PostMapping("/fields/{fieldId}/analyses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisResponse run(@PathVariable Long fieldId, @RequestBody(required = false) RunAnalysisRequest body) {
        return analysisService.run(fieldId, body);
    }

    @Operation(summary = "FAILED tahlilni qayta navbatga qo'yish", description = "Huquq: `analysis:run`")
    @PostMapping("/analyses/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisResponse retry(@PathVariable Long id) {
        return analysisService.retry(id);
    }

    // Tahlil rasmi o'zgarmaydi — brauzer keshlaydi; private: shaxsiy ma'lumot, proxy'da saqlanmasin
    private static ResponseEntity<byte[]> png(byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(bytes);
    }
}
