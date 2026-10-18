package uz.app.agro.onboarding;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.file.FileSource;
import uz.app.agro.file.dto.FileResponse;
import uz.app.agro.onboarding.dto.TutorialVideoResponse;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.AuthUser;
import uz.app.agro.security.CurrentActor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Yo'riqnoma videosi: admin har til uchun MP4 yuklaydi (≤ app.files.max-size), bot salomlashishdan keyin va ❓ Qo'llanma'da
 * fermer tilida yuboradi. Til uchun video bo'lmasa — uz, keyin en (ekran yozuvi tilsiz ham tushunarli).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TutorialVideoService {

    public static final List<String> LANGUAGES = List.of("uz", "ru", "en");

    private final TutorialVideoRepository videos;
    private final FileRegistry files;
    private final AuditService auditService;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final org.springframework.context.ApplicationEventPublisher events;

    /** Yangi video yuklandi — bot uni xodimlar guruhiga bir marta yuborib, Telegram file_id'ni saqlaydi */
    public record TutorialVideoUploaded(String language) {}

    /** Bot uchun (huquqsiz): yuboriladigan video — Telegram id (bor bo'lsa) yoki fayl */
    public record Video(String language, String fileHashId, String telegramFileId) {}

    public Optional<Video> forLanguage(String lang) {
        for (String l : List.of(lang, "uz", "en")) {
            Optional<TutorialVideoEntity> v = videos.findById(l);
            if (v.isPresent()) return v.map(e -> new Video(e.getLanguage(), e.getFileHashId(), e.getTelegramFileId()));
        }
        return Optional.empty();
    }

    @Transactional
    public void rememberTelegramId(Video v, String telegramFileId) {
        videos.rememberTelegramId(v.language(), v.fileHashId(), telegramFileId);
    }

    @PreAuthorize(Permissions.CAN_READ_ONBOARDING)
    public List<TutorialVideoResponse> list() {
        var all = videos.findAll();
        var infos = files.infos(all.stream().map(TutorialVideoEntity::getFileHashId).toList());
        return LANGUAGES.stream().map(l -> all.stream().filter(v -> v.getLanguage().equals(l)).findFirst()
                .map(v -> toResponse(v, infos.get(v.getFileHashId())))
                .orElse(new TutorialVideoResponse(l, null, null, null, null, null))).toList();
    }

    /**
     * Fayl diskka TRANZAKSIYADAN TASHQARIDA (FileRegistry o'z tranzaksiyasida, faqat MP4 qabul qiladi — boshqa tur saqlanmaydi),
     * keyin qisqa tranzaksiyada yozuv. NOT_SUPPORTED: klass darajasidagi readOnly tranzaksiya ochilmasin
     */
    @PreAuthorize(Permissions.CAN_MANAGE_ONBOARDING)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public TutorialVideoResponse upload(String lang, MultipartFile file) {
        guardLanguage(lang);
        if (file == null || file.isEmpty()) throw new BadRequestException("Video tanlanmagan");
        AuthUser me = CurrentActor.get().orElseThrow(() -> new IllegalStateException("autentifikatsiya yo'q"));
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Faylni o'qib bo'lmadi");
        }
        FileResponse stored = files.storeVideo(bytes, file.getOriginalFilename(),
                new FileRegistry.Uploader(FileSource.STAFF, me.id(), me.email()));
        TutorialVideoEntity v = tx.execute(s -> {
            TutorialVideoEntity e = videos.findById(lang).orElseGet(TutorialVideoEntity::new);
            e.setLanguage(lang);
            e.setFileHashId(stored.hashId());
            e.setTelegramFileId(null);                   // yangi fayl — Telegram'ga qayta yuklanadi
            e.setUpdatedDate(LocalDateTime.now());
            e.setUpdatedByEmail(me.email());
            videos.save(e);
            auditService.event(AuditEvent.ONBOARDING_CHANGED).target("TUTORIAL_VIDEO", null)
                    .detail("language", lang).detail("file", stored.hashId()).record();
            return e;
        });
        events.publishEvent(new TutorialVideoUploaded(lang));   // bot (bo'lsa) file_id'ni oldindan oladi
        return toResponse(v, stored);
    }

    @PreAuthorize(Permissions.CAN_MANAGE_ONBOARDING)
    @Transactional
    public void delete(String lang) {
        guardLanguage(lang);
        TutorialVideoEntity v = videos.findById(lang).orElseThrow(() -> new NotFoundException("Video", lang));
        videos.delete(v);
        auditService.event(AuditEvent.ONBOARDING_CHANGED).target("TUTORIAL_VIDEO", null)
                .detail("language", lang).detail("deleted", true).record();
    }

    private static void guardLanguage(String lang) {
        if (!LANGUAGES.contains(lang)) throw new BadRequestException("Til: uz, ru yoki en");
    }

    private static TutorialVideoResponse toResponse(TutorialVideoEntity v, FileResponse f) {
        return new TutorialVideoResponse(v.getLanguage(), v.getFileHashId(), f == null ? null : f.size(),
                f == null ? null : f.name(), v.getUpdatedDate(), v.getUpdatedByEmail());
    }
}
