package uz.app.agro.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;
import uz.app.agro.file.dto.FileAdminResponse;
import uz.app.agro.file.dto.FileResponse;
import uz.app.agro.file.dto.FileStats;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Fayllar", description = "Serverdagi papkada saqlanadigan fayllar (rasm, PDF). Murojaat — faqat hashId orqali")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "Fayl yuklash",
               description = "Huquq: `file:upload`. multipart `file`. JPG/PNG (qayta kodlanadi, EXIF/GPS o'chadi) yoki PDF, ≤ 10 MB")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(@RequestPart("file") MultipartFile file) {
        return fileService.upload(file);
    }

    private static final Set<String> SORTABLE = Set.of("createdDate", "sizeBytes", "originalName");

    @Operation(summary = "Barcha fayllar", description = "Huquq: `file:manage`. `type` — IMAGE / PDF; `q` — nom yoki hashId; "
            + "`from`/`to` — yuklangan sana. Har faylda — qayerda ishlatilgani (`usages`)")
    @GetMapping
    public PageResponse<FileAdminResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) FileSource source,
            @RequestParam(required = false) Long uploadedById,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 24, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return fileService.search(q, type, source, uploadedById, from, to, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Fayllar statistikasi", description = "Huquq: `file:manage`")
    @GetMapping("/stats")
    public FileStats stats() {
        return fileService.stats();
    }

    @Operation(summary = "Rasmning kichik nusxasi (400 px, JPEG)", description = "Huquq: `file:read`. Birinchi so'rovda yasaladi, diskda keshlanadi")
    @GetMapping("/{hashId}/thumb")
    public ResponseEntity<byte[]> thumb(@PathVariable String hashId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate().immutable())
                .body(fileService.thumbnail(hashId));
    }

    @Operation(summary = "Fayl ma'lumoti", description = "Huquq: `file:read`")
    @GetMapping("/{hashId}/info")
    public FileResponse info(@PathVariable String hashId) {
        return fileService.info(hashId);
    }

    @Operation(summary = "Fayl kontenti", description = "Huquq: `file:read`. Rasm — inline, PDF — yuklab olish (attachment)")
    @GetMapping("/{hashId}")
    public ResponseEntity<byte[]> content(@PathVariable String hashId) {
        FileRegistry.Content c = fileService.content(hashId);
        FileResponse f = c.info();
        // PDF brauzer ichida ochilmasin (o'z skript dvigateli bor) — yuklab olinadi; rasm — <img> uchun inline
        ContentDisposition cd = (f.image() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(f.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(c.bytes().length)
                .header("Content-Disposition", cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                // Kontent o'zgarmaydi (yangi fayl — yangi hashId) → brauzer keshlaydi; private — umumiy proxy'lar emas
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate().immutable())
                .eTag("\"" + c.info().hashId() + "\"")
                .body(c.bytes());
    }
}
