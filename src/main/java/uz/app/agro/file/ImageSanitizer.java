package uz.app.agro.file;

import uz.app.agro.common.exception.BadRequestException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Rasm QAYTA KODLANADI (asl baytlar saqlanmaydi):
 *  - EXIF/XMP o'chadi — telefon rasmida GPS (fermer uyi/dalasi), qurilma, vaqt bo'ladi;
 *  - rasm ichiga yashirilgan "polyglot" (rasm + HTML/skript) yo'qoladi;
 *  - EXIF orientatsiyasi pikselga qo'llanadi (aks holda metadata o'chgach telefon rasmi yonboshlab qoladi);
 *  - juda katta rasm kichraytiriladi; dekompressiya bombasi (kichik fayl → 100 000² piksel) — o'qishdan OLDIN rad.
 */
final class ImageSanitizer {

    record Result(byte[] bytes, int width, int height) {}

    private static final long MAX_PIXELS = 50_000_000L;   // 50 Mpx — telefon kamerasidan katta
    private static final int MAX_SIDE = 4096;             // saqlash/Telegram uchun yetarli
    private static final float JPEG_QUALITY = 0.9f;

    static {
        ImageIO.setUseCache(false);                       // vaqtinchalik fayl yozmasin — xotirada
    }

    private ImageSanitizer() {}

    static Result sanitize(byte[] input, FileTypes type) {
        BufferedImage img = read(input);
        if (type == FileTypes.JPEG) img = orient(img, exifOrientation(input));
        img = limit(img);
        return new Result(write(img, type), img.getWidth(), img.getHeight());
    }

    private static final int THUMB_SIDE = 400;

    /** Ro'yxat uchun kichik nusxa (JPEG). Kirish — allaqachon tozalangan fayl (bizning diskdan) */
    static byte[] thumbnail(byte[] stored) {
        BufferedImage img = read(stored);
        int w = img.getWidth(), h = img.getHeight();
        double k = Math.min(1.0, (double) THUMB_SIDE / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * k)), nh = Math.max(1, (int) Math.round(h * k));
        // JPEG'da shaffoflik yo'q — PNG'ning shaffof joylari oq fon bo'ladi (qora emas)
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, AffineTransform.getScaleInstance(k, k), null);
        } finally {
            g.dispose();
        }
        return write(dst, FileTypes.JPEG);
    }

    private static BufferedImage read(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new BadRequestException("Rasm o'qilmadi");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long px = (long) reader.getWidth(0) * reader.getHeight(0);
                if (px > MAX_PIXELS) throw new BadRequestException("Rasm o'lchami juda katta (50 Mpx dan ko'p)");
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException b) throw b;
            // CMYK JPEG, buzilgan fayl va h.k.
            throw new BadRequestException("Rasm o'qilmadi — boshqa formatda (JPG/PNG) saqlab qayta yuboring");
        }
    }

    private static byte[] write(BufferedImage img, FileTypes type) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (type == FileTypes.PNG) {
                ImageIO.write(img, "png", out);
                return out.toByteArray();
            }
            // JPEG'da alfa kanal yo'q — RGB'ga o'tkazamiz (aks holda rang buziladi yoki yozilmaydi)
            BufferedImage rgb = img.getType() == BufferedImage.TYPE_INT_RGB || img.getType() == BufferedImage.TYPE_3BYTE_BGR
                    ? img : redraw(img, img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB, new AffineTransform());
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam p = writer.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(JPEG_QUALITY);
                writer.write(null, new IIOImage(rgb, null, null), p);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("rasm yozilmadi", e);
        }
    }

    private static BufferedImage limit(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (Math.max(w, h) <= MAX_SIDE) return img;
        double k = (double) MAX_SIDE / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * k)), nh = Math.max(1, (int) Math.round(h * k));
        return redraw(img, nw, nh, alphaType(img), AffineTransform.getScaleInstance(k, k));
    }

    /** EXIF orientation 1–8 (TIFF 0x0112). Ko'zgu holatlari (2,4,5,7) telefonlarda deyarli uchramaydi — faqat burilish */
    private static BufferedImage orient(BufferedImage img, int orientation) {
        int w = img.getWidth(), h = img.getHeight();
        AffineTransform t = new AffineTransform();
        return switch (orientation) {
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); yield redraw(img, w, h, alphaType(img), t); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); yield redraw(img, h, w, alphaType(img), t); }
            case 8 -> { t.translate(0, w); t.rotate(-Math.PI / 2); yield redraw(img, h, w, alphaType(img), t); }
            default -> img;
        };
    }

    private static int alphaType(BufferedImage img) {
        return img.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    private static BufferedImage redraw(BufferedImage src, int w, int h, int type, AffineTransform t) {
        BufferedImage dst = new BufferedImage(w, h, type);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** JPEG APP1 "Exif" → TIFF IFD0 → 0x0112. Topilmasa yoki buzuq bo'lsa — 1 (o'zgarishsiz) */
    static int exifOrientation(byte[] b) {
        try {
            int i = 2;
            while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
                int marker = b[i + 1] & 0xFF;
                if (marker == 0xDA || marker == 0xD9) break;                  // SOS — rasm ma'lumoti boshlandi
                int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
                if (len < 2) break;
                if (marker == 0xE1 && i + 10 <= b.length && b[i + 4] == 'E' && b[i + 5] == 'x' && b[i + 6] == 'i'
                        && b[i + 7] == 'f' && b[i + 8] == 0 && b[i + 9] == 0) {
                    int t = i + 10;
                    boolean le = b[t] == 'I' && b[t + 1] == 'I';
                    int ifd = t + read32(b, t + 4, le);
                    int n = read16(b, ifd, le);
                    for (int k = 0; k < n; k++) {
                        int e = ifd + 2 + k * 12;
                        if (e + 12 > b.length) break;
                        if (read16(b, e, le) == 0x0112) {
                            int v = read16(b, e + 8, le);
                            return v >= 1 && v <= 8 ? v : 1;
                        }
                    }
                    return 1;
                }
                i += 2 + len;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // buzuq EXIF — orientatsiyasiz davom etamiz
        }
        return 1;
    }

    private static int read16(byte[] b, int o, boolean le) {
        return le ? (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) : ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static int read32(byte[] b, int o, boolean le) {
        return le ? (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24)
                : ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }
}
