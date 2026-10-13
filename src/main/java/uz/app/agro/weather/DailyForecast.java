package uz.app.agro.weather;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Bir kunlik prognoz. Qiymat yo'q bo'lsa (model bermadi) — null; jsonb'da Map ko'rinishida saqlanadi */
public record DailyForecast(LocalDate date, Double tMin, Double tMax, Double gustMs, Double rainMm, Integer code) {

    Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();            // Map.of null qabul qilmaydi (CLAUDE.md 5.6)
        m.put("date", date.toString());
        m.put("tMin", tMin);
        m.put("tMax", tMax);
        m.put("gustMs", gustMs);
        m.put("rainMm", rainMm);
        m.put("code", code);
        return m;
    }

    static DailyForecast fromMap(Map<String, Object> m) {
        return new DailyForecast(LocalDate.parse((String) m.get("date")), num(m.get("tMin")), num(m.get("tMax")),
                num(m.get("gustMs")), num(m.get("rainMm")), m.get("code") == null ? null : ((Number) m.get("code")).intValue());
    }

    private static Double num(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }
}
