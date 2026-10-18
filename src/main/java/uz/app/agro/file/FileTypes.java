package uz.app.agro.file;

import java.util.Optional;

/**
 * Turi MAGIC BYTES bo'yicha aniqlanadi — kengaytma va mijoz yuborgan Content-Type'ga ishonilmaydi
 * ("rasm.jpg" ichida HTML yoki exe bo'lishi mumkin). Ruxsat etilgan ro'yxat — oq ro'yxat.
 */
enum FileTypes {

    JPEG("image/jpeg", ".jpg", true),
    PNG("image/png", ".png", true),
    PDF("application/pdf", ".pdf", false),
    MP4("video/mp4", ".mp4", false);            // yo'riqnoma videosi (admin yuklaydi)

    /** MP4 "ftyp" major brand'lari (QuickTime .mov, HEIC/AVIF rasmlar — emas) */
    private static final java.util.Set<String> MP4_BRANDS = java.util.Set.of("isom", "iso2", "iso4", "iso5", "iso6",
            "mp41", "mp42", "avc1", "M4V ");

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
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p'
                && MP4_BRANDS.contains(new String(b, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1))) return Optional.of(MP4);
        return Optional.empty();
    }

    static Optional<FileTypes> byContentType(String contentType) {
        for (FileTypes t : values()) if (t.contentType.equals(contentType)) return Optional.of(t);
        return Optional.empty();
    }
}
