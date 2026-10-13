package uz.app.agro.weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Prognozdagi xavf. Sof mantiq (DB/tarmoqsiz) — chegaralar Sozlamalardan keladi.
 * Faqat yaqin 3 kun (bugun + 2): uzoqroq prognoz ishonchsiz, u faqat botdagi "🌦 Ob-havo" da ko'rinadi.
 */
public record WeatherRisk(Kind kind, Level level, LocalDate date, double value) {

    public enum Kind { FROST, HEAT, WIND, RAIN }

    public enum Level { WARNING, DANGER }

    /** Chegaralar: chegaraga yetsa — WARNING; frost−3 / heat+3 / wind+5 / rain×2 — DANGER */
    public record Thresholds(int frostC, int heatC, int windMs, int rainMm) {}

    static final int HORIZON_DAYS = 2;
    /** Bugungi xavf shu soatdan keyin aytilmaydi — kunduzgi issiq/shamol/jala o'tib bo'lgan yoki ketyapti */
    static final int TODAY_CUTOFF_HOUR = 14;
    private static final int FROST_STEP = 3, HEAT_STEP = 3, WIND_STEP = 5;

    /**
     * @param localToday / localHour — dala mahalliy vaqti (katakning utc_offset'i)
     * @param southern — janubiy yarimshar (Argentina): fasllar teskari, sovuq oylari ham
     */
    public static List<WeatherRisk> detect(List<DailyForecast> days, LocalDate localToday, int localHour,
                                           boolean southern, Thresholds t) {
        List<WeatherRisk> out = new ArrayList<>();
        for (DailyForecast d : days) {
            if (d.date().isBefore(localToday) || d.date().isAfter(localToday.plusDays(HORIZON_DAYS))) continue;
            boolean today = d.date().equals(localToday);
            for (WeatherRisk r : check(d, southern, t)) {
                // Bugungi minimal harorat — tong payti, tun sukut soatlarida o'tib bo'lgan (bu tungi sovuq ertangi
                // sana bilan keladi); qolganlari — tushdan keyin kech
                if (today && (r.kind() == Kind.FROST || localHour >= TODAY_CUTOFF_HOUR)) continue;
                out.add(r);
            }
        }
        return out;
    }

    /** Bir kun — vaqtdan qat'i nazar (botdagi 5 kunlik prognozda belgi uchun ham) */
    public static List<WeatherRisk> check(DailyForecast d, boolean southern, Thresholds t) {
        List<WeatherRisk> out = new ArrayList<>();
        if (d.tMin() != null && frostSeason(d.date(), southern) && d.tMin() <= t.frostC()) {
            out.add(new WeatherRisk(Kind.FROST, d.tMin() <= t.frostC() - FROST_STEP ? Level.DANGER : Level.WARNING, d.date(), d.tMin()));
        }
        if (d.tMax() != null && d.tMax() >= t.heatC()) {
            out.add(new WeatherRisk(Kind.HEAT, d.tMax() >= t.heatC() + HEAT_STEP ? Level.DANGER : Level.WARNING, d.date(), d.tMax()));
        }
        if (d.gustMs() != null && d.gustMs() >= t.windMs()) {
            out.add(new WeatherRisk(Kind.WIND, d.gustMs() >= t.windMs() + WIND_STEP ? Level.DANGER : Level.WARNING, d.date(), d.gustMs()));
        }
        boolean hail = d.code() != null && (d.code() == 96 || d.code() == 99);   // WMO: momaqaldiroq + do'l
        double rain = d.rainMm() == null ? 0 : d.rainMm();
        if (hail || rain >= t.rainMm()) {
            out.add(new WeatherRisk(Kind.RAIN, hail || rain >= 2.0 * t.rainMm() ? Level.DANGER : Level.WARNING, d.date(), rain));
        }
        return out;
    }

    /**
     * Sovuq faqat ekin dalada bo'lganda xavfli: bahor (kech sovuq) va kuz (erta sovuq).
     * Qishda har kecha sovuq — oddiy hol, ogohlantirish spam bo'lardi.
     */
    static boolean frostSeason(LocalDate date, boolean southern) {
        int m = date.getMonthValue();
        if (southern) m = (m + 5) % 12 + 1;               // janub: mart ↔ sentyabr
        return (m >= 3 && m <= 5) || (m >= 9 && m <= 11);
    }
}
