package uz.app.agro.file;

import java.util.Optional;

/**
 * Turi MAGIC BYTES bo'yicha aniqlanadi — kengaytma va mijoz yuborgan Content-Type'ga ishonilmaydi
 * ("rasm.jpg" ichida HTML yoki exe bo'lishi mumkin). Ruxsat etilgan ro'yxat — oq ro'yxat.
 */
enum FileTypes {

    JPEG("image/jpeg", ".jpg", true),
    PNG("image/png", ".png", true),
    PDF("application/pdf", ".pdf", false);

    final String contentType;
    final String extension;
    final boolean image;

    FileTypes(String contentType, String extension, boolean image) {
        this.contentType = contentType;
        this.extension = extension;
        this.image = image;
    }

    static Optional<FileTypes> detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Optional.of(JPEG);
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return Optional.of(PNG);
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return Optional.of(PDF);
        return Optional.empty();
    }

    static Optional<FileTypes> byContentType(String contentType) {
        for (FileTypes t : values()) if (t.contentType.equals(contentType)) return Optional.of(t);
        return Optional.empty();
    }
}
