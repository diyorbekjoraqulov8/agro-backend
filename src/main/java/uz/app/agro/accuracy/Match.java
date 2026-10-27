package uz.app.agro.accuracy;

import uz.app.agro.accuracy.ObservationEntity.Condition;
import uz.app.agro.analysis.AnalysisJob;
import uz.app.agro.monitoring.MonitoringEventEntity;
import uz.app.agro.monitoring.Verdict;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Kuzatuv ↔ tizim xulosasi avtomatik solishtirish. reason — admin'da matnga o'giriladi.
 * Taxminiy: tahlil faqat ENG KATTA sust bo'lak markazini saqlaydi (debug.weak_lat/lon), zona chegarasini emas —
 * radius = sust ulush maydoniga teng doira + 1 piksel. Bir nechta sust bo'lakda kichiklari "uzoq" chiqishi mumkin.
 */
public record Match(Result result, String reason, Long refId, Double distanceM) {

    public enum Result { AGREE, DISAGREE, NOT_APPLICABLE, NO_DATA }

    static final int ANALYSIS_WINDOW_DAYS = 45;                  // tahlil zonalari mavsum cho'qqisidan — peak_date ± shuncha kun
    static final int MONITORING_BEFORE_DAYS = 21, MONITORING_AFTER_DAYS = 7;
    static final double GOOD_MAX_WEAK_PCT = 15;                  // "butun dala yaxshi" — sust ulush shundan kam bo'lsa mos
    static final double PIXEL_M = 30;                            // radius zaxirasi: 10 m piksel + chet + GPS xatosi

    private static Match of(Result r, String reason) {
        return new Match(r, reason, null, null);
    }

    static Match analysis(ObservationEntity o, List<AnalysisJob> doneJobs) {
        if (o.getCondition() == Condition.HARVESTED) return of(Result.NOT_APPLICABLE, "HARVESTED");
        AnalysisJob job = nearest(o.getObservedOn(), doneJobs);
        if (job == null) return of(Result.NO_DATA, "NO_ANALYSIS");
        Map<String, Object> r = job.getResult();
        double weakPct = num(r.get("zones") instanceof Map<?, ?> z ? z.get("weak_pct") : null, 0);
        boolean problem = o.getCondition() != Condition.GOOD;

        if (!o.hasPoint()) {
            // Zonalar dala medianasiga NISBATAN — butun dala bir tekis yomon bo'lsa ko'rinmaydi (L3: yillararo solishtirish)
            if (problem) return new Match(Result.NOT_APPLICABLE, "RELATIVE_ZONES", job.getId(), null);
            return new Match(weakPct < GOOD_MAX_WEAK_PCT ? Result.AGREE : Result.DISAGREE,
                    weakPct < GOOD_MAX_WEAK_PCT ? "LOW_WEAK" : "WEAK_ZONE_FOUND", job.getId(), null);
        }

        if (!(r.get("debug") instanceof Map<?, ?> d)) return new Match(Result.NO_DATA, "NO_DEBUG", job.getId(), null);
        Object wLat = d.get("weak_lat"), wLon = d.get("weak_lon");
        if (weakPct <= 0 || wLat == null || wLon == null) {
            // Nuqtali muammo, tizim esa sust zona topmagan — DISAGREE (butun dala bir tekis yomon bo'lsa ham nisbiy zona
            // ko'rmaydi; bunday holat uchun admin "butun dala" kuzatuvini yozadi → RELATIVE_ZONES)
            return new Match(problem ? Result.DISAGREE : Result.AGREE, "NO_WEAK_ZONE", job.getId(), null);
        }
        double areaHa = num(r.get("area_ha"), 0);
        double radius = Math.sqrt(weakPct / 100 * areaHa * 10_000 / Math.PI) + PIXEL_M;
        double dist = distanceM(o.getLat().doubleValue(), o.getLon().doubleValue(), num(wLat, 0), num(wLon, 0));
        boolean near = dist <= radius;
        double rounded = Math.round(dist * 10) / 10.0;
        if (problem) return new Match(near ? Result.AGREE : Result.DISAGREE, near ? "NEAR_WEAK" : "FAR_FROM_WEAK", job.getId(), rounded);
        return new Match(near ? Result.DISAGREE : Result.AGREE, near ? "IN_WEAK_ZONE" : "OUTSIDE_WEAK", job.getId(), rounded);
    }

    static Match monitoring(ObservationEntity o, List<MonitoringEventEntity> fieldEvents) {
        LocalDate from = o.getObservedOn().minusDays(MONITORING_BEFORE_DAYS), to = o.getObservedOn().plusDays(MONITORING_AFTER_DAYS);
        // BASELINE — birinchi surat, solishtirish yo'q: xulosa emas
        List<MonitoringEventEntity> win = fieldEvents.stream()
                .filter(e -> e.getVerdict() != null && e.getVerdict() != Verdict.BASELINE)
                .filter(e -> !e.getSceneDate().isBefore(from) && !e.getSceneDate().isAfter(to)).toList();
        if (win.isEmpty()) return of(Result.NO_DATA, "NO_EVENTS");
        return switch (o.getCondition()) {
            case HARVESTED -> pick(win, Result.AGREE, "HARVEST_DETECTED", Result.DISAGREE, "NOT_DETECTED", Verdict.HARVEST, Verdict.NO_CROP);
            case GOOD -> pick(win, Result.DISAGREE, "FALSE_DECLINE", Result.AGREE, "NO_DECLINE", Verdict.DECLINE);
            // Do'l/yotib qolish butun dalani bir tekis tushiradi — monitoring uni HARVEST (bir tekis pasayish) deb ko'radi
            case DAMAGED -> pick(win, Result.AGREE, "DECLINE_DETECTED", Result.DISAGREE, "NO_DECLINE", Verdict.DECLINE, Verdict.HARVEST);
            case BARE -> pick(win, Result.AGREE, "DECLINE_DETECTED", Result.DISAGREE, "NO_DECLINE", Verdict.DECLINE, Verdict.NO_CROP);
            case WEAK -> pick(win, Result.AGREE, "DECLINE_DETECTED", Result.DISAGREE, "NO_DECLINE", Verdict.DECLINE);
        };
    }

    private static Match pick(List<MonitoringEventEntity> win, Result hit, String hitReason, Result miss, String missReason,
                              Verdict... verdicts) {
        List<Verdict> wanted = List.of(verdicts);
        return win.stream().filter(e -> wanted.contains(e.getVerdict())).findFirst()
                .map(e -> new Match(hit, hitReason, e.getId(), null))
                .orElseGet(() -> new Match(miss, missReason, win.getLast().getId(), null));
    }

    /**
     * Zonalar tahlil yaratilgan kunni emas, mavsum CHO'QQISINI aks ettiradi — kuzatuvga peak_date eng yaqin tahlil.
     * Teng bo'lsa — eng yangisi (barqaror natija)
     */
    private static AnalysisJob nearest(LocalDate day, List<AnalysisJob> jobs) {
        return jobs.stream()
                .filter(j -> j.getResult() != null && peakDate(j) != null)
                .filter(j -> Math.abs(ChronoUnit.DAYS.between(day, peakDate(j))) <= ANALYSIS_WINDOW_DAYS)
                .min(Comparator.<AnalysisJob>comparingLong(j -> Math.abs(ChronoUnit.DAYS.between(day, peakDate(j))))
                        .thenComparing(AnalysisJob::getId, Comparator.reverseOrder()))
                .orElse(null);
    }

    private static LocalDate peakDate(AnalysisJob j) {
        try {
            return j.getResult().get("peak_date") instanceof String s ? LocalDate.parse(s) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double num(Object v, double fallback) {
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double r = Math.toRadians(1);
        double h = Math.pow(Math.sin((lat2 - lat1) * r / 2), 2)
                + Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.pow(Math.sin((lon2 - lon1) * r / 2), 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
    }
}
