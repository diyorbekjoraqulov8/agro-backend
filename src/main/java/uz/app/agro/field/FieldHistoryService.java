package uz.app.agro.field;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.analysis.AnalysisJob;
import uz.app.agro.analysis.AnalysisJobRepository;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.field.dto.FieldHistoryResponse;
import uz.app.agro.field.dto.FieldHistoryResponse.Event;
import uz.app.agro.field.dto.FieldHistoryResponse.NdviPoint;
import uz.app.agro.field.dto.FieldHistoryResponse.Season;
import uz.app.agro.monitoring.MonitoringEventEntity;
import uz.app.agro.monitoring.MonitoringEventRepository;
import uz.app.agro.notification.NotificationEntity;
import uz.app.agro.notification.NotificationRepository;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.weather.WeatherAlertEntity;
import uz.app.agro.weather.WeatherAlertRepository;
import uz.app.agro.weather.WeatherCell;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * L1b: dala holati tarixi bitta javobda — NDVI egri chizig'i (tahlil + monitoring) va hodisalar vaqt chizig'i.
 * So'rovlar: dala, mavsumlar, tahlillar, monitoring, ob-havo, xabarlar — 6 ta, davr uzunligidan qat'i nazar.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldHistoryService {

    static final int MAX_DAYS = 3 * 366;
    static final int ANALYSIS_SPAN_DAYS = 367;                  // AnalysisService: oxirgi 12 oy

    private final FieldRepository fieldRepository;
    private final FieldSeasonRepository seasonRepository;
    private final AnalysisJobRepository jobRepository;
    private final MonitoringEventRepository monitoringRepository;
    private final WeatherAlertRepository weatherRepository;
    private final NotificationRepository notificationRepository;

    /** from/to null — joriy + oldingi mavsum (mavsum yo'q bo'lsa oxirgi 12 oy) */
    @PreAuthorize(Permissions.CAN_READ_FIELD_HISTORY)
    public FieldHistoryResponse history(Long fieldId, LocalDate from, LocalDate to) {
        FieldEntity field = fieldRepository.findById(fieldId).orElseThrow(() -> new NotFoundException("Dala", fieldId));
        List<FieldSeasonEntity> seasons = seasonRepository.findByFieldIdOrderByIdDesc(fieldId);

        LocalDate today = LocalDate.now();
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : to != null ? to.minusYears(1) : defaultStart(seasons, today);
        if (start.isAfter(end)) throw new BadRequestException("Davr boshi oxiridan keyin");
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) throw new BadRequestException("Davr 3 yildan oshmasin");

        List<NdviPoint> ndvi = new ArrayList<>();
        List<Event> events = new ArrayList<>();

        // Tahlil o'tgan 12 oyni qamraydi — davr nuqtalari davrdan keyingi 12 oyda yaratilgan tahlilda ham bor
        List<AnalysisJob> jobs = jobRepository.findTop200ByFieldIdAndCreatedDateBetweenOrderByIdDesc(fieldId,
                start.atStartOfDay(), end.plusDays(ANALYSIS_SPAN_DAYS).atStartOfDay());
        ndvi.addAll(analysisPoints(jobs, start, end));
        for (AnalysisJob j : jobs) {
            LocalDate d = j.getCreatedDate() == null ? null : j.getCreatedDate().toLocalDate();
            if (d == null || d.isBefore(start) || d.isAfter(end)) continue;
            events.add(new Event(j.getCreatedDate(), "ANALYSIS", j.getStatus().name(), j.getId(), analysisData(j)));
        }

        for (MonitoringEventEntity e : monitoringRepository.findByFieldIdAndSceneDateBetweenOrderBySceneDate(fieldId, start, end)) {
            if (e.getNdvi() != null) ndvi.add(new NdviPoint(e.getSceneDate(), e.getNdvi().doubleValue(), null, "MONITORING", e.getId()));
            Map<String, Object> data = new HashMap<>();             // Map.of null qabul qilmaydi
            data.put("sceneDate", e.getSceneDate());
            data.put("refDate", e.getRefDate());
            data.put("ndvi", e.getNdvi());
            data.put("ndviRef", e.getNdviRef());
            data.put("declinePct", e.getDeclinePct());
            data.put("declineSide", e.getDeclineSide());
            data.put("improvePct", e.getImprovePct());
            data.put("hasMap", e.getMapFileHashId() != null);
            events.add(new Event(e.getCreatedDate() != null ? e.getCreatedDate() : e.getSceneDate().atStartOfDay(),
                    "MONITORING", e.getVerdict() != null ? e.getVerdict().name() : null, e.getId(), data));
        }

        long farmerId = field.getFarmer().getId();                  // proxy — farmer qatori o'qilmaydi
        String cellKey = field.getCentroidLat() != null && field.getCentroidLon() != null
                ? WeatherCell.of(field.getCentroidLat(), field.getCentroidLon()).key() : null;
        if (cellKey != null) {
            for (WeatherAlertEntity a : weatherRepository.findByFarmerIdAndCellKeyAndEventDateBetweenOrderByEventDate(
                    farmerId, cellKey, start, end)) {
                Map<String, Object> data = new HashMap<>();
                data.put("kind", a.getKind().name());
                data.put("eventDate", a.getEventDate());
                data.put("value", a.getValue());
                events.add(new Event(a.getCreatedDate(), "WEATHER", a.getLevel().name(), a.getId(), data));
            }
        }

        for (NotificationEntity n : notificationRepository.forField(farmerId, fieldId, cellKey != null ? cellKey : "",
                start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
            Map<String, Object> data = new HashMap<>();
            data.put("category", n.getCategory() != null ? n.getCategory().name() : null);
            data.put("template", n.getTemplate());
            data.put("preview", n.getPreview());
            data.put("lastError", n.getLastError());
            events.add(new Event(n.getCreatedDate(), "NOTIFICATION", n.getStatus().name(), n.getId(), data));
        }

        ndvi.sort(Comparator.comparing(NdviPoint::date));
        events.sort(Comparator.comparing(Event::at, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return new FieldHistoryResponse(start, end, seasons.stream().map(FieldHistoryService::season).toList(), ndvi, events);
    }

    /**
     * Oldingi mavsum boshi. Tizimda bitta mavsum bo'lsa (yangi dala) — kamida 12 oy: tahlil o'tgan yilni ham ko'rgan,
     * aks holda bugun qo'shilgan dalada grafik bitta kunga tushardi
     */
    private static LocalDate defaultStart(List<FieldSeasonEntity> seasons, LocalDate today) {
        LocalDate yearAgo = today.minusYears(1);
        if (seasons.size() < 2) {
            LocalDate s = seasons.isEmpty() ? yearAgo : startOf(seasons.getFirst());
            return s.isBefore(yearAgo) ? s : yearAgo;
        }
        return startOf(seasons.get(1));
    }

    private static LocalDate startOf(FieldSeasonEntity s) {
        LocalDate created = s.getCreatedDate() != null ? s.getCreatedDate().toLocalDate() : LocalDate.now();
        return s.getSownOn() != null && s.getSownOn().isBefore(created) ? s.getSownOn() : created;
    }

    private static Season season(FieldSeasonEntity s) {
        return new Season(s.getId(), s.getCrop(), s.getSowing(), s.getSownOn(), startOf(s), s.getFinishedOn(), s.getStatus());
    }

    /** Tahlillar 12 oyni qamraydi va ustma-ust tushadi — har sana uchun eng yangi tahlil qiymati (jobs: yangi → eski) */
    private static List<NdviPoint> analysisPoints(List<AnalysisJob> jobs, LocalDate start, LocalDate end) {
        Map<LocalDate, NdviPoint> byDate = new TreeMap<>();
        for (AnalysisJob j : jobs) {
            if (j.getStatus() != AnalysisJob.Status.DONE || j.getResult() == null) continue;
            if (!(j.getResult().get("timeseries") instanceof List<?> ts)) continue;
            for (Object o : ts) {
                if (!(o instanceof Map<?, ?> p) || !(p.get("ndvi") instanceof Number v) || p.get("date") == null) continue;
                LocalDate d;
                try {
                    d = LocalDate.parse(p.get("date").toString());
                } catch (RuntimeException e) {
                    continue;
                }
                if (d.isBefore(start) || d.isAfter(end)) continue;
                Double ndmi = p.get("ndmi") instanceof Number m ? m.doubleValue() : null;
                byDate.putIfAbsent(d, new NdviPoint(d, v.doubleValue(), ndmi, "ANALYSIS", j.getId()));
            }
        }
        return new ArrayList<>(byDate.values());
    }

    /** Ro'yxat uchun qisqa ko'rinish; to'liq natija (debug bilan) — GET /analyses/{id} */
    private static Map<String, Object> analysisData(AnalysisJob j) {
        Map<String, Object> data = new HashMap<>();
        data.put("kind", j.getKind().name());
        data.put("error", j.getError());
        Map<String, Object> r = j.getResult();
        if (r != null) {
            data.put("peakNdvi", r.get("peak_ndvi"));
            data.put("peakDate", r.get("peak_date"));
            data.put("zones", r.get("zones"));
            data.put("scenesUsed", r.get("scenes_used"));
            data.put("scenesRejected", r.get("scenes_rejected_cloud"));
        }
        return data;
    }
}
