package uz.app.agro.onboarding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.onboarding.dto.StepRequest;
import uz.app.agro.onboarding.dto.StepResponse;
import uz.app.agro.onboarding.dto.TutorialVideoResponse;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Onboarding", description = "Botdagi /start'dan keyingi xabar va savollar — qadamma-qadam, 3 tilda")
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingAdminService service;
    private final TutorialVideoService videos;

    @Operation(summary = "Yo'riqnoma videolari (uz, ru, en)", description = "Huquq: `onboarding:read`. fileHashId null — yuklanmagan")
    @GetMapping("/videos")
    public List<TutorialVideoResponse> videos() {
        return videos.list();
    }

    @Operation(summary = "Yo'riqnoma videosini yuklash/almashtirish",
               description = "Huquq: `onboarding:manage`. multipart `file`, faqat MP4, ≤ 10 MB. Bot salomlashishdan keyin va ❓ Qo'llanma'da yuboradi")
    @PutMapping(path = "/videos/{lang}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TutorialVideoResponse uploadVideo(@PathVariable String lang, @RequestPart("file") MultipartFile file) {
        return videos.upload(lang, file);
    }

    @Operation(summary = "Yo'riqnoma videosini o'chirish", description = "Huquq: `onboarding:manage`")
    @DeleteMapping("/videos/{lang}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVideo(@PathVariable String lang) {
        videos.delete(lang);
    }

    @Operation(summary = "Qadamlar (bot tartibida)", description = "Huquq: `onboarding:read`. answers — nechta fermer javob bergan")
    @GetMapping("/steps")
    public List<StepResponse> list() {
        return service.list();
    }

    @Operation(summary = "Yangi xabar yoki savol", description = "Huquq: `onboarding:manage`. Yakuniy xabardan oldin qo'shiladi")
    @PostMapping("/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepResponse create(@Valid @RequestBody StepRequest body) {
        return service.create(body);
    }

    @Operation(summary = "Tahrirlash", description = "Huquq: `onboarding:manage`. Matn, variantlar (maxsus savol), faollik")
    @PutMapping("/steps/{id}")
    public StepResponse update(@PathVariable Long id, @Valid @RequestBody StepRequest body) {
        return service.update(id, body);
    }

    @Operation(summary = "Tartibni o'zgartirish", description = "Huquq: `onboarding:manage`. direction: up | down")
    @PostMapping("/steps/{id}/move")
    public List<StepResponse> move(@PathVariable Long id, @RequestParam String direction) {
        if (!direction.equalsIgnoreCase("up") && !direction.equalsIgnoreCase("down")) {
            throw new BadRequestException("direction: up | down");
        }
        return service.move(id, direction.equalsIgnoreCase("up"));
    }

    @Operation(summary = "O'chirish", description = "Huquq: `onboarding:manage`. Faqat qo'shilgan (o'rnatilmagan) qadam")
    @DeleteMapping("/steps/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
