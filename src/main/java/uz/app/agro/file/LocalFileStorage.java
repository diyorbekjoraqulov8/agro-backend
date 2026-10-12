package uz.app.agro.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Fayllar serverdagi papkada (app.files.dir). Faqat shu klass disk bilan ishlaydi — keyin S3'ga o'tilsa
 * almashtiriladigan yagona joy. Yo'l har doim ildiz ichida ekani tekshiriladi (path traversal).
 */
@Slf4j
@Component
class LocalFileStorage {

    private final Path root;

    LocalFileStorage(FileProperties props) throws IOException {
        this.root = Path.of(props.dir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (!Files.isWritable(root)) throw new IllegalStateException("app.files.dir yozib bo'lmaydi: " + root);
        log.info("fayllar papkasi: {}", root);
    }

    /** Atomik: avval vaqtinchalik fayl, keyin bir harakatda ko'chirish — yarim yozilgan fayl hech qachon ko'rinmaydi */
    void write(String relativePath, byte[] content) {
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(tmp, content);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("fayl yozilmadi: " + relativePath, e);
        }
    }

    byte[] read(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    boolean exists(String relativePath) {
        return Files.isRegularFile(resolve(relativePath));
    }

    /** DB'ga yozish muvaffaqiyatsiz bo'lsa — yetim fayl qolmasin */
    void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | RuntimeException e) {
            log.warn("fayl o'chirilmadi {}: {}", relativePath, e.getMessage());
        }
    }

    private Path resolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("papkadan tashqari yo'l: " + relativePath);
        return p;
    }
}
