package uz.app.agro.field;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Dala chegarasining arzon tekshiruvlari — bot (Mini App) va admin (Lab dala) uchun BITTA joyda.
 * O'z-o'zini kesish va maydonning aniq chegarasini Python tekshiradi (tahlil 422).
 */
public final class Rings {

    public static final int MAX_VERTICES = 200;
    private static final double LAT_MIN = -60, LAT_MAX = 75;
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private Rings() {}

    public static boolean validPoint(double lon, double lat) {
        return Double.isFinite(lon) && Double.isFinite(lat) && lon >= -180 && lon <= 180 && lat >= LAT_MIN && lat <= LAT_MAX;
    }

    /** Yopilmagan bo'lsa yopadi (ring o'zgaradi). @return false — 3 tadan kam yoki MAX_VERTICES dan ko'p uch */
    public static boolean close(List<double[]> ring) {
        if (ring.isEmpty()) return false;
        double[] first = ring.getFirst(), last = ring.getLast();
        if (first[0] != last[0] || first[1] != last[1]) ring.add(new double[]{first[0], first[1]});
        return ring.size() >= 4 && ring.size() <= MAX_VERTICES + 1;
    }

    /** Nomdagi boshqaruv belgilari olib tashlanadi, 80 belgigacha qisqartiriladi; bo'sh bo'lsa — defaultName */
    public static String name(String raw, String defaultName) {
        String name = raw == null ? "" : CONTROL.matcher(raw).replaceAll("").strip();
        if (name.isEmpty()) return defaultName;
        return name.codePointCount(0, name.length()) > 80 ? name.substring(0, name.offsetByCodePoints(0, 80)) : name;
    }
}
