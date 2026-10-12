package uz.app.agro.file;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.app.agro.common.PageResponse;
import uz.app.agro.file.dto.FileAdminResponse;
import uz.app.agro.file.dto.FileResponse;
import uz.app.agro.file.dto.FileStats;
import uz.app.agro.file.dto.FileUsage;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.AuthUser;
import uz.app.agro.security.CurrentActor;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xodimlar uchun fayl API'si. Ruxsat — hash_id'ni bilish + file:read. hash_id 128 bit tasodifiy va faqat
 * fayl biriktirilgan joyda (masalan o'zi ko'ra oladigan suhbat xabarida) ko'rinadi.
 * Tranzaksiya yo'q — FileRegistry diskka yozadi, so'ng bitta INSERT.
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRegistry registry;
    private final FileRepository repository;
    private final List<FileUsageProvider> usageProviders;     // har feature o'zi aytadi (hozircha murojaatlar)

    /** Admin: barcha fayllar — qayerda ishlatilgani bilan (sahifa uchun bitta so'rov har provayderga) */
    @PreAuthorize(Permissions.CAN_MANAGE_FILE)
    public PageResponse<FileAdminResponse> search(String q, String type, FileSource source, Long uploadedById,
                                                  LocalDate from, LocalDate to, Pageable pageable) {
        Page<FileEntity> page = repository.findAll(FileSpecs.filter(q, type, source, uploadedById, from, to), pageable);
        Set<String> ids = page.getContent().stream().map(FileEntity::getHashId).collect(Collectors.toSet());
        Map<String, List<FileUsage>> usages = new HashMap<>();
        if (!ids.isEmpty()) {
            for (FileUsageProvider p : usageProviders) {
                p.usages(ids).forEach((k, v) -> usages.computeIfAbsent(k, x -> new ArrayList<>()).addAll(v));
            }
        }
        return PageResponse.of(page.map(f -> {
            FileResponse r = FileRegistry.toResponse(f);
            return new FileAdminResponse(r.hashId(), r.name(), r.contentType(), r.size(), r.width(), r.height(), r.image(),
                    r.url(), r.image() ? r.url() + "/thumb" : null, f.getSha256(), f.getSource(), f.getUploadedByEmail(),
                    r.createdDate(), usages.getOrDefault(f.getHashId(), List.of()));
        }));
    }

    @PreAuthorize(Permissions.CAN_MANAGE_FILE)
    public FileStats stats() {
        FileRepository.StatsRow s = repository.stats();
        return new FileStats(s.getTotal(), s.getTotalBytes(), s.getImages(), s.getPdfs(), s.getFromTelegram(), s.getFromStaff());
    }

    @PreAuthorize(Permissions.CAN_READ_FILE)
    public byte[] thumbnail(String hashId) {
        return registry.thumbnail(hashId);
    }

    @PreAuthorize(Permissions.CAN_UPLOAD_FILE)
    public FileResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Fayl tanlanmagan");
        AuthUser me = CurrentActor.get().orElseThrow(() -> new IllegalStateException("autentifikatsiya yo'q"));
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Fayl o'qilmadi");
        }
        return registry.store(bytes, file.getOriginalFilename(),
                new FileRegistry.Uploader(FileSource.STAFF, me.id(), me.email()));
    }

    @PreAuthorize(Permissions.CAN_READ_FILE)
    public FileResponse info(String hashId) {
        return registry.info(hashId).orElseThrow(() -> new NotFoundException("Fayl", hashId));
    }

    @PreAuthorize(Permissions.CAN_READ_FILE)
    public FileRegistry.Content content(String hashId) {
        return registry.content(hashId);
    }
}
