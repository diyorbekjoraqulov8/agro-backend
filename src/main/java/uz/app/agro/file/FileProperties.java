package uz.app.agro.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * @param dir     fayllar papkasi. Nisbiy bo'lsa — ishga tushirilgan joyga (IntelliJ'da loyiha ildizi) nisbatan.
 *                DB'da faqat shu papkaga NISBIY yo'l saqlanadi — papka ko'chirilsa faqat shu sozlama o'zgaradi
 * @param maxSize bitta fayl chegarasi (spring.servlet.multipart.max-file-size bilan bir xil bo'lsin)
 */
@Validated
@ConfigurationProperties(prefix = "app.files")
public record FileProperties(
        @NotBlank(message = "app.files.dir bo'sh — FILES_DIR='' yozilgan bo'lishi mumkin") String dir,
        @NotNull DataSize maxSize
) {}
