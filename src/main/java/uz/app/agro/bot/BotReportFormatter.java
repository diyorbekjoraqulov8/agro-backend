package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.field.Crop;
import uz.app.agro.satellite.dto.AnalyzeResponse;
import uz.app.agro.satellite.dto.AnalyzeResponse.Recommendation;
import uz.app.agro.satellite.dto.PdfRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Python javobi → FERMER tushunadigan matn (NDVI, ET₀, mm kabi atamalarsiz).
 * Telegram'ga bog'liq EMAS — bot o'chiq bo'lsa ham sinaladi. Bitta manba → 3 ko'rinish:
 * Telegram (emoji bilan), PDF (emoji'siz — DejaVu'da yo'q), CSV (Excel).
 * Raqamlar oldindan formatlanadi — MessageFormat {0} ni locale bo'yicha "7,680" qilib yubormasin.
 */
@Component
@RequiredArgsConstructor
public class BotReportFormatter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Set<String> SIDES = Set.of("N", "NE", "E", "SE", "S", "SW", "W", "NW", "CENTER");
    // So (emoji, ✅, 🟢...) + variation selector / ZWJ — PDF shriftida yo'q
    private static final Pattern NON_PDF = Pattern.compile("[\\p{So}\\x{FE0F}\\x{200D}\\x{20E3}]");
    static final int MAX_RECOMMENDATIONS = 3;

    private final BotMessages m;

    // ── Telegram ───────────────────────────────────────────────────────────

    /** 1-rasm (dala xaritasi) izohi: dala, holat, mavsum */
    public String mapCaption(String lang, String fieldName, Crop crop, AnalyzeResponse r) {
        List<String> lines = new ArrayList<>();
        lines.add(m.text(lang, "report.title", fieldName, num(lang, r.areaHa(), r.areaHa() < 10 ? 2 : 1), m.label(lang, crop)));
        lines.add("");
        lines.addAll(healthLines(lang, r));
        lines.add("");
        lines.add(m.text(lang, "report.legend"));
        return String.join("\n", lines);
    }

    public String chartCaption(String lang) {
        return m.text(lang, "report.chart");
    }

    /** 3-xabar: suv + maslahatlar + tarif; ostida fayl tugmalari */
    public String summary(String lang, AnalyzeResponse r, EffectivePlan plan) {
        List<String> lines = new ArrayList<>(waterLines(lang, r));
        List<String> recs = recommendations(lang, r.recommendations());
        if (!recs.isEmpty()) {
            lines.add("");
            lines.add(m.text(lang, "report.advice"));
            recs.forEach(x -> lines.add("• " + x));
        }
        lines.add("");
        lines.add(footer(lang, plan));
        lines.add("");
        lines.add(m.text(lang, "report.files"));
        return String.join("\n", lines);
    }

    // ── PDF / CSV ──────────────────────────────────────────────────────────

    public PdfRequest pdf(String lang, String fieldName, Crop crop, LocalDate seasonEnd, AnalyzeResponse r, EffectivePlan plan) {
        List<PdfRequest.Section> sections = new ArrayList<>();
        // healthLines'ning 1-qatori ("Dalangiz holati:") — PDF'da sarlavha bo'lib turibdi
        sections.add(new PdfRequest.Section(m.text(lang, "pdf.health"), plain(healthLines(lang, r).subList(1, healthLines(lang, r).size()))));
        sections.add(new PdfRequest.Section(m.text(lang, "pdf.water"), plain(waterLines(lang, r))));
        List<String> recs = recommendations(lang, r.recommendations());
        if (!recs.isEmpty()) sections.add(new PdfRequest.Section(heading(m.text(lang, "report.advice")), plain(recs.stream().map(x -> "• " + x).toList())));
        sections.add(new PdfRequest.Section(m.text(lang, "pdf.howToRead"), List.of(
                plain(m.text(lang, "report.legend")), plain(m.text(lang, "report.chart").replace("\n", " ")))));
        sections.add(new PdfRequest.Section(m.text(lang, "pdf.plan"), List.of(plain(footer(lang, plan)))));

        Base64.Encoder b64 = Base64.getEncoder();
        return new PdfRequest(
                plain(m.text(lang, "pdf.title", fieldName)),
                plain(m.text(lang, "pdf.subtitle", m.label(lang, crop), num(lang, r.areaHa(), 2),
                        r.countryCode() == null ? "—" : r.countryCode(), seasonEnd.format(DAY_YEAR))),
                m.text(lang, "pdf.footer"),
                sections,
                List.of(new PdfRequest.Image(m.text(lang, "pdf.map"), b64.encodeToString(r.mapPng())),
                        new PdfRequest.Image(m.text(lang, "pdf.chart"), b64.encodeToString(r.chartPng()))),
                new PdfRequest.Table(m.text(lang, "pdf.table"),
                        List.of(m.text(lang, "csv.date"), m.text(lang, "csv.growth"), m.text(lang, "csv.moisture")),
                        r.timeseries().stream().map(p -> List.of(p.date().format(DAY_YEAR),
                                num(lang, p.ndvi(), 2), num(lang, p.ndmi(), 2))).toList()));
    }

    /**
     * Excel ochadigan CSV: UTF-8 BOM (aks holda kirill/ʻ buziladi), ajratuvchi ";" (uz/ru Excel'da
     * vergul — kasr belgisi). Tepada qisqa xulosa, keyin kuzatuvlar.
     */
    public byte[] csv(String lang, String fieldName, Crop crop, AnalyzeResponse r) {
        StringBuilder sb = new StringBuilder("﻿");
        row(sb, m.text(lang, "csv.field"), fieldName);
        row(sb, m.text(lang, "csv.crop"), plain(m.label(lang, crop)));
        row(sb, m.text(lang, "csv.area"), num(lang, r.areaHa(), 2));
        row(sb, m.text(lang, "csv.good"), pct(r.zones().goodPct()));
        row(sb, m.text(lang, "csv.mid"), pct(r.zones().midPct()));
        row(sb, m.text(lang, "csv.weak"), pct(r.zones().weakPct()));
        if (r.water() != null) {
            row(sb, m.text(lang, "csv.water"), r.water().irrigationNeedLowM3Ha() + "–" + r.water().irrigationNeedHighM3Ha());
        }
        sb.append("\r\n");
        row(sb, m.text(lang, "csv.date"), m.text(lang, "csv.growth"), m.text(lang, "csv.moisture"));
        for (var p : r.timeseries()) row(sb, p.date().toString(), num(lang, p.ndvi(), 3), num(lang, p.ndmi(), 3));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Umumiy qismlar ─────────────────────────────────────────────────────

    private List<String> healthLines(String lang, AnalyzeResponse r) {
        List<String> lines = new ArrayList<>();
        var z = r.zones();
        lines.add(m.text(lang, "report.health"));
        lines.add(m.text(lang, "report.zone.good", pct(z.goodPct())));
        lines.add(m.text(lang, "report.zone.mid", pct(z.midPct())));
        lines.add(z.weakSide() != null && SIDES.contains(z.weakSide()) && !"CENTER".equals(z.weakSide()) && z.weakPct() >= 5
                ? m.text(lang, "report.zone.weakWhere", pct(z.weakPct()), m.text(lang, "side." + z.weakSide()))
                : m.text(lang, "report.zone.weak", pct(z.weakPct())));
        if (r.growingSeason() != null) {
            lines.add(m.text(lang, "report.season", day(r.growingSeason().from()), day(r.growingSeason().to())));
            lines.add(m.text(lang, "report.best", day(r.peakDate())));
        }
        if (r.prevYearPeakNdvi() != null && r.prevYearPeakNdvi() > 0) {
            long diff = Math.round((r.peakNdvi() / r.prevYearPeakNdvi() - 1) * 100);
            lines.add(diff <= -5 ? m.text(lang, "report.prevYear.lower", String.valueOf(-diff))
                    : diff >= 5 ? m.text(lang, "report.prevYear.higher", String.valueOf(diff))
                    : m.text(lang, "report.prevYear.same"));
        }
        return lines;
    }

    private List<String> waterLines(String lang, AnalyzeResponse r) {
        if (r.water() == null) return List.of(m.text(lang, "report.noWater"));
        var w = r.water();
        List<String> lines = new ArrayList<>();
        lines.add(m.text(lang, "report.water", thousands(lang, w.irrigationNeedLowM3Ha()), thousands(lang, w.irrigationNeedHighM3Ha())));
        long rainM3 = Math.round(w.effectiveRainMm() * 10 / 100.0) * 100;     // 1 mm = 10 m³/ga
        if (rainM3 >= 100) lines.add(m.text(lang, "report.water.rain", thousands(lang, (int) rainM3)));
        return lines;
    }

    private String footer(String lang, EffectivePlan plan) {
        return plan != null && plan.paid()
                ? m.text(lang, "report.footer.paid", plan.planName(), plan.validUntil() == null ? "—" : plan.validUntil().format(DAY_YEAR))
                : m.text(lang, "report.footer.free");
    }

    List<String> recommendations(String lang, List<Recommendation> recs) {
        List<String> out = new ArrayList<>();
        if (recs == null) return out;
        for (Recommendation rec : recs) {
            if (out.size() >= MAX_RECOMMENDATIONS) break;
            String line = recommendation(lang, rec);
            // "{1} часть поля…" — tomon nomi bilan boshlansa ham gap katta harfdan
            if (line != null && !line.isEmpty()) out.add(line.substring(0, 1).toUpperCase(Locale.ROOT) + line.substring(1));
        }
        return out;
    }

    // Noma'lum kod — o'tkazib yuboriladi: Python yangi tavsiya qo'shsa Java yiqilmasin
    private String recommendation(String lang, Recommendation rec) {
        Map<String, Object> p = rec.params() == null ? Map.of() : rec.params();
        try {
            return switch (rec.code()) {
                case "WATER_STRESS" -> m.text(lang, "rec.WATER_STRESS",
                        day(LocalDate.parse(String.valueOf(p.get("from")))), day(LocalDate.parse(String.valueOf(p.get("to")))));
                case "WEAK_ZONE" -> {
                    String side = String.valueOf(p.get("side"));
                    yield m.text(lang, "rec.WEAK_ZONE", pct(((Number) p.get("weak_pct")).doubleValue()),
                            SIDES.contains(side) ? m.text(lang, "side." + side) : m.text(lang, "side.CENTER"));
                }
                case "BELOW_LAST_YEAR" -> m.text(lang, "rec.BELOW_LAST_YEAR", String.valueOf(((Number) p.get("pct")).intValue()));
                case "LOW_CONFIDENCE", "FIELD_UNIFORM", "NO_CROP_DETECTED" -> m.text(lang, "rec." + rec.code());
                default -> null;
            };
        } catch (RuntimeException e) {                 // parametr yo'q/noto'g'ri tip — tavsiyasiz qolsin
            return null;
        }
    }

    public String fileCaption(String lang, String key, String fieldName) {
        return m.text(lang, key, fieldName);
    }

    public String fileFailed(String lang) {
        return m.text(lang, "file.failed");
    }

    public String technical(String lang) {
        return m.text(lang, "fail.technical");
    }

    public String rejected(String lang, String code) {
        return switch (code) {
            case "NO_SCENES" -> m.text(lang, "fail.noScenes");
            case "SELF_INTERSECTING", "AREA_TOO_SMALL", "AREA_TOO_LARGE", "TOO_MANY_VERTICES", "TOO_FEW_VERTICES",
                 "OUT_OF_RANGE", "CROSSES_ANTIMERIDIAN" -> m.text(lang, "fail.polygon", m.text(lang, "fail.reason." + code));
            default -> m.text(lang, "fail.polygon", m.text(lang, "fail.reason.OTHER"));
        };
    }

    // ── Formatlash ─────────────────────────────────────────────────────────

    // "💡 Maslahatlar:" → "Maslahatlar"
    private static String heading(String s) {
        String p = plain(s);
        return p.endsWith(":") ? p.substring(0, p.length() - 1) : p;
    }

    static String plain(String s) {
        return NON_PDF.matcher(s).replaceAll("").replaceAll("  +", " ").strip();
    }

    private static List<String> plain(List<String> lines) {
        return lines.stream().map(BotReportFormatter::plain).filter(x -> !x.isEmpty()).toList();
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(';');
            String c = cells[i] == null ? "" : cells[i];
            // CSV injection: =,+,-,@ bilan boshlangan katak Excel'da formula bo'lib ishlaydi
            if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0 && !c.matches("-?\\d+([.,]\\d+)?")) c = "'" + c;
            sb.append(c.contains(";") || c.contains("\"") || c.contains("\n") ? '"' + c.replace("\"", "\"\"") + '"' : c);
        }
        sb.append("\r\n");
    }

    private static String day(LocalDate d) {
        return d == null ? "?" : (d.getYear() == LocalDate.now().getYear() ? d.format(DAY) : d.format(DAY_YEAR));
    }

    private static String pct(double v) {
        return String.valueOf(Math.round(v));
    }

    // uz/ru — vergul (12,4), en — nuqta (12.4)
    static String num(String lang, double v, int digits) {
        String s = String.format(Locale.ROOT, "%." + digits + "f", v);
        return "en".equals(lang) ? s : s.replace('.', ',');
    }

    private static String thousands(String lang, int v) {
        String s = String.format(Locale.ROOT, "%,d", v);
        return "en".equals(lang) ? s : s.replace(',', ' ');
    }
}
