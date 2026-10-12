package uz.app.agro.bot;

import java.util.List;

/**
 * Google Encoded Polyline (precision 1e6 — Mini App bilan bir xil, app.js decodePolyline).
 * Tahrirlashda eski chegara Mini App URL'iga shu bilan qo'shiladi: 200 nuqta ≈ 1,5 KB (JSON ~6 KB).
 */
final class Polyline {

    private Polyline() {}

    /** @param ring [[lon, lat], ...] (GeoJSON tartibi); natijada lat,lon (polyline tartibi) */
    static String encode(List<List<Number>> ring) {
        StringBuilder sb = new StringBuilder();
        long pLat = 0, pLon = 0;
        int n = ring.size();
        // Yopiq halqaning oxirgi nuqtasi (= birinchi) yuborilmaydi — Mini App o'zi yopadi
        if (n > 1 && ring.getFirst().get(0).doubleValue() == ring.getLast().get(0).doubleValue()
                && ring.getFirst().get(1).doubleValue() == ring.getLast().get(1).doubleValue()) n--;
        for (int i = 0; i < n; i++) {
            long lat = Math.round(ring.get(i).get(1).doubleValue() * 1e6);
            long lon = Math.round(ring.get(i).get(0).doubleValue() * 1e6);
            write(sb, lat - pLat);
            write(sb, lon - pLon);
            pLat = lat;
            pLon = lon;
        }
        return sb.toString();
    }

    private static void write(StringBuilder sb, long v) {
        long x = v < 0 ? ~(v << 1) : v << 1;
        while (x >= 0x20) {
            sb.append((char) ((0x20 | (x & 0x1f)) + 63));
            x >>= 5;
        }
        sb.append((char) (x + 63));
    }
}
