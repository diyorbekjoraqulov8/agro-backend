package uz.app.agro.accuracy;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.accuracy.FindingReviewEntity.TargetType;
import uz.app.agro.accuracy.dto.*;
import uz.app.agro.analysis.AnalysisJob;
import uz.app.agro.analysis.AnalysisJobRepository;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.monitoring.MonitoringEventEntity;
import uz.app.agro.monitoring.MonitoringEventRepository;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.CurrentActor;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** L2: ground truth (kuzatuvlar), xulosalarga baho va aniqlik hisoboti */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccuracyService {

    private final ObservationRepository observations;
    private final FindingReviewRepository reviews;
    private final FieldRepository fieldRepository;
    private final AnalysisJobRepository jobRepository;
    private final MonitoringEventRepository monitoringRepository;
    private final AuditService auditService;

    @PreAuthorize(Permissions.CAN_WRITE_FIELD)
    @Transactional
    public ObservationResponse create(Long fieldId, ObservationRequest req) {
        FieldEntity field = fieldRepository.findById(fieldId).orElseThrow(() -> new NotFoundException("Dala", fieldId));
        ObservationEntity o = new ObservationEntity();
        o.setFieldId(fieldId);
        apply(o, req, field);
        o.setAuthorEmail(CurrentActor.get().map(a -> a.email()).orElse(null));
        observations.save(o);
        audit(AuditEvent.OBSERVATION_SAVED, o);
        return ObservationResponse.of(o);
    }

    @PreAuthorize(Permissions.CAN_WRITE_FIELD)
    @Transactional
    public ObservationResponse update(Long id, ObservationRequest req) {
        ObservationEntity o = observations.findById(id).orElseThrow(() -> new NotFoundException("Kuzatuv", id));
        FieldEntity field = fieldRepository.findById(o.getFieldId()).orElseThrow(() -> new NotFoundException("Kuzatuv", id));
        apply(o, req, field);
        audit(AuditEvent.OBSERVATION_SAVED, o);
        return ObservationResponse.of(o);
    }

    @PreAuthorize(Permissions.CAN_WRITE_FIELD)
    @Transactional
    public void delete(Long id) {
        ObservationEntity o = observations.findById(id).orElseThrow(() -> new NotFoundException("Kuzatuv", id));
        if (!fieldRepository.existsById(o.getFieldId())) throw new NotFoundException("Kuzatuv", id);   // dala o'chirilgan
        observations.delete(o);                                  // soft delete
        audit(AuditEvent.OBSERVATION_DELETED, o);
    }

    /** Qayta baholash — ustiga yoziladi (UNIQUE target_type, target_id) */
    @PreAuthorize(Permissions.CAN_WRITE_FIELD)
    @Transactional
    public ReviewResponse review(TargetType type, Long targetId, ReviewRequest req) {
        Long fieldId = fieldOf(type, targetId);
        reviews.upsert(type.name(), targetId, fieldId, req.verdict().name(), blankToNull(req.note()),
                CurrentActor.get().map(a -> a.email()).orElse(null));
        FindingReviewEntity r = reviews.findByTargetTypeAndTargetId(type, targetId).orElseThrow();
        auditService.event(AuditEvent.FINDING_REVIEWED).target(type.name(), targetId)
                .detail("verdict", req.verdict().name()).detail("fieldId", fieldId).record();
        return ReviewResponse.of(r);
    }

    @PreAuthorize(Permissions.CAN_WRITE_FIELD)
    @Transactional
    public void removeReview(TargetType type, Long targetId) {
        FindingReviewEntity r = reviews.findByTargetTypeAndTargetId(type, targetId)
                .orElseThrow(() -> new NotFoundException("Baho", targetId));
        reviews.delete(r);
        auditService.event(AuditEvent.FINDING_REVIEWED).target(type.name(), targetId).detail("removed", true).record();
    }

    /**
     * Aniqlik: qo'lda baholar soni + har kuzatuvni tizim xulosasi bilan avtomatik solishtirish.
     * So'rovlar: baholar, kuzatuvlar (2), tahlillar, monitoring — 5 ta, kuzatuvlar sonidan qat'i nazar.
     */
    @PreAuthorize(Permissions.CAN_READ_FIELD_HISTORY)
    public AccuracyReport report(Boolean lab) {
        Map<String, Map<String, Long>> manual = new TreeMap<>();
        String labParam = lab == null ? null : lab.toString();
        for (var c : reviews.counts(labParam)) {
            manual.computeIfAbsent(c.getTargetType(), k -> new TreeMap<>()).merge(c.getVerdict(), c.getCount(), Long::sum);
        }

        List<ObservationRepository.Row> meta = observations.report(labParam);
        Map<Long, ObservationEntity> byId = observations.findAllById(meta.stream().map(ObservationRepository.Row::getId).toList())
                .stream().collect(Collectors.toMap(ObservationEntity::getId, Function.identity()));
        Set<Long> fieldIds = meta.stream().map(ObservationRepository.Row::getFieldId).collect(Collectors.toSet());

        Map<Long, List<AnalysisJob>> jobs = Map.of();
        Map<Long, List<MonitoringEventEntity>> events = Map.of();
        if (!fieldIds.isEmpty()) {
            LocalDate min = byId.values().stream().map(ObservationEntity::getObservedOn).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate max = byId.values().stream().map(ObservationEntity::getObservedOn).max(Comparator.naturalOrder()).orElseThrow();
            // Faqat davri kuzatuvlar oralig'i bilan kesishadigan tahlillar — ko'p yillik tarix butunlay o'qilmasin
            jobs = jobRepository.findByField_IdInAndStatusAndSeasonEndGreaterThanEqualAndSeasonStartLessThanEqual(
                            fieldIds, AnalysisJob.Status.DONE, min.minusDays(Match.ANALYSIS_WINDOW_DAYS),
                            max.plusDays(Match.ANALYSIS_WINDOW_DAYS)).stream()
                    .collect(Collectors.groupingBy(j -> j.getField().getId()));   // proxy id — dala o'qilmaydi
            events = monitoringRepository.findByFieldIdInAndSceneDateBetweenOrderBySceneDate(fieldIds,
                            min.minusDays(Match.MONITORING_BEFORE_DAYS), max.plusDays(Match.MONITORING_AFTER_DAYS)).stream()
                    .collect(Collectors.groupingBy(MonitoringEventEntity::getFieldId));
        }

        Map<String, Map<Match.Result, Long>> auto = new TreeMap<>();
        List<AccuracyReport.Row> rows = new ArrayList<>();
        for (ObservationRepository.Row m : meta) {
            ObservationEntity o = byId.get(m.getId());
            if (o == null) continue;
            Match a = Match.analysis(o, jobs.getOrDefault(m.getFieldId(), List.of()));
            Match e = Match.monitoring(o, events.getOrDefault(m.getFieldId(), List.of()));
            auto.computeIfAbsent("ANALYSIS", k -> new EnumMap<>(Match.Result.class)).merge(a.result(), 1L, Long::sum);
            auto.computeIfAbsent("MONITORING", k -> new EnumMap<>(Match.Result.class)).merge(e.result(), 1L, Long::sum);
            rows.add(new AccuracyReport.Row(ObservationResponse.of(o), m.getFieldName(), m.getLab(), a, e));
        }
        return new AccuracyReport(manual, auto, rows, meta.size() >= ObservationRepository.REPORT_LIMIT);
    }

    private Long fieldOf(TargetType type, Long targetId) {
        return switch (type) {
            case ANALYSIS -> {
                AnalysisJob j = jobRepository.findById(targetId).orElseThrow(() -> new NotFoundException("Tahlil", targetId));
                if (j.getStatus() != AnalysisJob.Status.DONE) throw new ConflictException("Faqat tugagan tahlil baholanadi");
                Long fieldId = j.getField().getId();
                if (!fieldRepository.existsById(fieldId)) throw new NotFoundException("Tahlil", targetId);  // dala o'chirilgan
                yield fieldId;
            }
            case MONITORING -> {
                MonitoringEventEntity e = monitoringRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Monitoring hodisasi", targetId));
                if (!fieldRepository.existsById(e.getFieldId())) throw new NotFoundException("Monitoring hodisasi", targetId);
                yield e.getFieldId();
            }
        };
    }

    private static void apply(ObservationEntity o, ObservationRequest req, FieldEntity field) {
        if ((req.lat() == null) != (req.lon() == null)) throw new BadRequestException("Nuqta uchun lat va lon ikkalasi kerak");
        if (req.lat() != null && !inside(field, req.lon().doubleValue(), req.lat().doubleValue())) {
            throw new BadRequestException("Nuqta dala chegarasidan tashqarida");
        }
        if (req.observedOn().isBefore(LocalDate.now().minusYears(5))) throw new BadRequestException("Kuzatuv sanasi 5 yildan eski");
        o.setObservedOn(req.observedOn());
        o.setCondition(req.condition());
        o.setCause(req.condition() == ObservationEntity.Condition.GOOD ? null : req.cause());
        o.setLat(req.lat() == null ? null : req.lat().setScale(6, java.math.RoundingMode.HALF_UP));
        o.setLon(req.lon() == null ? null : req.lon().setScale(6, java.math.RoundingMode.HALF_UP));
        o.setNote(blankToNull(req.note()));
    }

    /** Ray casting — dala tashqi halqasi (GeoJSON [lon, lat]) */
    @SuppressWarnings("unchecked")
    private static boolean inside(FieldEntity field, double lon, double lat) {
        Object coords = field.getBoundaryGeojson() == null ? null : field.getBoundaryGeojson().get("coordinates");
        if (!(coords instanceof List<?> rings) || rings.isEmpty() || !(rings.getFirst() instanceof List<?> ring)) return false;
        boolean in = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            List<Number> a = (List<Number>) ring.get(i), b = (List<Number>) ring.get(j);
            double ax = a.get(0).doubleValue(), ay = a.get(1).doubleValue(), bx = b.get(0).doubleValue(), by = b.get(1).doubleValue();
            if ((ay > lat) != (by > lat) && lon < (bx - ax) * (lat - ay) / (by - ay) + ax) in = !in;
        }
        return in;
    }

    private void audit(AuditEvent event, ObservationEntity o) {
        auditService.event(event).target("OBSERVATION", o.getId())
                .detail("fieldId", o.getFieldId())
                .detail("condition", o.getCondition().name())
                .detail("observedOn", o.getObservedOn().toString())
                .record();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
