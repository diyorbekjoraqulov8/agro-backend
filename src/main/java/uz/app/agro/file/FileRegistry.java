package uz.app.agro.file;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.file.dto.FileResponse;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ichki fayl servisi — @PreAuthorize YO'Q: bot (fermer rasmi) va boshqa servislar chaqiradi.
 * Controller'ga ULANMASIN: xodimlar uchun — FileService.
 *
 * Saqlash: tur (magic bytes) → rasm bo'lsa qayta kodlash → diskka (atomik) → DB. DB xato bersa fayl o'chiriladi.
 * Tranzaksiya OCHILMAYDI: disk yozuvi DB ulanishini band qilmasin; bitta INSERT — repository'ning o'z tranzaksiyasi.
 */
@Service
@RequiredArgsConstructor
public class FileRegistry {

    public static final Pattern HASH_ID = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final SecureRandom RANDOM = new SecureRandom();
    // Fayl nomida path, boshqaruv belgilari, header'ni buzadigan belgilar bo'lmasin
    private static final Pattern UNSAFE_NAME = Pattern.compile("[\\p{Cntrl}/\\\\\"<>:|?*]");

    private final FileRepository repository;
    private final LocalFileStorage storage;
    private final FileProperties props;

    /** Kim yukladi — xodim (id, email) yoki fermer (Telegram) */
    public record Uploader(FileSource source, Long staffId, String staffEmail) {
        public static Uploader telegram() { return new Uploader(FileSource.TELEGRAM, null, null); }
        public static Uploader system() { return new Uploader(FileSource.SYSTEM, null, null); }
    }

    /** Fayl va metadata — tranzaksiyadan tashqarida ishlatish uchun (masalan Telegram'ga yuborish) */
    public record Content(FileResponse info, byte[] bytes) {}

    public FileResponse store(byte[] input, String originalName, Uploader by) {
        if (input == null || input.length == 0) throw new BadRequestException("Fayl bo'sh");
        if (input.length > props.maxSize().toBytes()) {
            throw new BadRequestException("Fayl juda katta — ko'pi bilan " + props.maxSize().toMegabytes() + " MB");
        }
        FileTypes type = FileTypes.detect(input)
                .orElseThrow(() -> new BadRequestException("Faqat JPG, PNG rasm yoki PDF hujjat yuklash mumkin"));
        byte[] bytes = input;
        Integer width = null, height = null;
        if (type.image) {
            ImageSanitizer.Result r = ImageSanitizer.sanitize(input, type);
            bytes = r.bytes();
            width = r.width();
            height = r.height();
        }

        String hashId = newHashId();
        LocalDate d = LocalDate.now();
        String path = "%d/%02d/%s%s".formatted(d.getYear(), d.getMonthValue(), hashId, type.extension);
        storage.write(path, bytes);

        FileEntity f = new FileEntity();
        f.setHashId(hashId);
        f.setPath(path);
        f.setOriginalName(safeName(originalName, type));
        f.setContentType(type.contentType);
        f.setSizeBytes(bytes.length);
        f.setSha256(sha256(bytes));
        f.setWidth(width);
        f.setHeight(height);
        f.setSource(by.source());
        f.setUploadedById(by.staffId());
        f.setUploadedByEmail(by.staffEmail());
        try {
            repository.save(f);
        } catch (RuntimeException e) {
            storage.deleteQuietly(path);
            throw e;
        }
        return toResponse(f);
    }

    public Optional<FileEntity> find(String hashId) {
        if (hashId == null || !HASH_ID.matcher(hashId).matches()) return Optional.empty();
        return repository.findByHashId(hashId);
    }

    public Optional<FileResponse> info(String hashId) {
        return find(hashId).map(FileRegistry::toResponse);
    }

    /** Ro'yxat uchun — bitta so'rov (N+1 emas) */
    public Map<String, FileResponse> infos(Collection<String> hashIds) {
        if (hashIds.isEmpty()) return Map.of();
        return repository.findByHashIdIn(hashIds).stream()
                .collect(Collectors.toMap(FileEntity::getHashId, FileRegistry::toResponse, (a, b) -> a));
    }

    public Content content(String hashId) {
        FileEntity f = find(hashId).orElseThrow(() -> new NotFoundException("Fayl", hashId));
        try {
            return new Content(toResponse(f), storage.read(f.getPath()));
        } catch (IOException e) {
            // DB'da bor, diskda yo'q — papka ko'chirilgan/backup'dan tiklanmagan
            throw new NotFoundException("Fayl", hashId);
        }
    }

    /**
     * Kichik nusxa: birinchi so'rovda yasaladi va diskka keshlanadi — thumbs/<asl yo'l>.jpg (yo'l asl fayldan
     * kelib chiqadi, DB'da saqlanmaydi). Rasm bo'lmasa — 404.
     */
    public byte[] thumbnail(String hashId) {
        FileEntity f = find(hashId).filter(x -> x.getContentType().startsWith("image/"))
                .orElseThrow(() -> new NotFoundException("Fayl", hashId));
        String thumb = "thumbs/" + f.getPath() + ".jpg";
        try {
            if (storage.exists(thumb)) return storage.read(thumb);
            byte[] bytes = ImageSanitizer.thumbnail(storage.read(f.getPath()));
            storage.write(thumb, bytes);                // atomik — ikki parallel so'rov ham buzmaydi
            return bytes;
        } catch (IOException e) {
            throw new NotFoundException("Fayl", hashId);
        }
    }

    static FileResponse toResponse(FileEntity f) {
        return new FileResponse(f.getHashId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                f.getWidth(), f.getHeight(), f.getContentType().startsWith("image/"),
                "/api/v1/files/" + f.getHashId(), f.getCreatedDate());
    }

    /** 128 bit tasodifiy — ketma-ket id'dan farqli, boshqa fayllarni sanab chiqib bo'lmaydi */
    private static String newHashId() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String safeName(String name, FileTypes type) {
        String n = name == null ? "" : name;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);   // brauzer to'liq yo'l yuborishi mumkin
        n = UNSAFE_NAME.matcher(n).replaceAll("_").strip();
        if (n.isEmpty() || n.startsWith(".")) n = "file" + n;
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        if (base.length() > 120) base = base.substring(0, 120);
        return base + type.extension;                                              // kengaytma — haqiqiy turga mos
    }

    private static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
