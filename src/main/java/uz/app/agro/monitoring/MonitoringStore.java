package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.billing.PlanService;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.notification.Templates;
import uz.app.agro.satellite.dto.WaterDtos.MonitorResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tranzaksiya chegarasi — job'dan alohida bean (ADR #24). Python (tarmoq) bu yerda CHAQIRILMAYDI.
 * Hodisa yozuvi va fermerga xabar — bitta tranzaksiyada (outbox).
 */
@Component
@RequiredArgsConstructor
class MonitoringStore {

    static final int MAX_BACKOFF = 7;

    /** paid — fermerning bugun faol pullik obunasi bor (ogohlantirish va tezroq tekshiruv shunga bog'liq) */
    record Input(long fieldId, String fieldName, long farmerId, Map<String, Object> geometry, LocalDate since, boolean paid) {}

    private final FieldMonitoringRepository queue;
    private final MonitoringEventRepository events;
    private final FieldRepository fields;
    private final NotificationService notifications;
    private final PlanService plans;

    @Transactional
    public int enroll(boolean paidOnly) {
        return queue.enroll(paidOnly);
    }

    /** Navbatdan olish (SKIP LOCKED) va band qilish — ikki instans bir dalani parallel tekshirmaydi */
    @Transactional
    public List<Long> claim(boolean paidOnly, int limit, String lease) {
        List<Long> ids = queue.lockDue(paidOnly, limit);
        if (!ids.isEmpty()) queue.lease(ids, lease);
        return ids;
    }

    /** Shu surat hali ko'rilmaganmi — xarita (fayl) saqlashdan OLDIN: aks holda bog'lanmagan PNG qolardi */
    @Transactional(readOnly = true)
    public boolean isNewScene(long fieldId, LocalDate sceneDate) {
        return !events.existsByFieldIdAndSceneDate(fieldId, sceneDate);
    }

    /** Python'ga yuboriladigan narsa — LAZY tranzaksiya ichida o'qiladi. Dala o'chirilgan — bo'sh */
    @Transactional(readOnly = true)
    public Optional<Input> input(long fieldId) {
        FieldEntity f = fields.findById(fieldId).orElse(null);
        FieldMonitoringEntity m = queue.findById(fieldId).orElse(null);
        if (f == null || m == null) return Optional.empty();
        long farmerId = f.getFarmer().getId();
        return Optional.of(new Input(fieldId, f.getName(), farmerId, new HashMap<>(f.getBoundaryGeojson()),
                later(m.getLastSceneDate(), m.getLastSeenDate()), plans.paidOn(farmerId, LocalDate.now())));
    }

    /** Yangi ochiq surat yo'q — keyingi tekshiruv; seen — metadata ko'rgan (bulutli) surat sanasi, keyingi `since` */
    @Transactional
    public void reschedule(long fieldId, LocalDateTime next, LocalDate seen) {
        queue.findById(fieldId).ifPresent(m -> {
            m.setNextCheckAt(next);
            m.setLastCheckedAt(LocalDateTime.now());
            m.setFailures(0);
            m.setLastSeenDate(later(m.getLastSeenDate(), seen));
        });
    }

    /** Xato — ketma-ket xatolar soniga qarab kamroq urinish: base × failures (ko'pi bilan MAX_BACKOFF marta) */
    @Transactional
    public void fail(long fieldId, Duration base) {
        queue.findById(fieldId).ifPresent(m -> {
            m.setFailures(m.getFailures() + 1);
            m.setNextCheckAt(LocalDateTime.now().plus(base.multipliedBy(Math.min(m.getFailures(), MAX_BACKOFF))));
            m.setLastCheckedAt(LocalDateTime.now());
        });
    }

    /** @return true — fermerga xabar navbatga qo'yildi */
    @Transactional
    public boolean record(Input in, MonitorResponse r, Verdict verdict, String mapHashId, LocalDateTime next, boolean alert) {
        FieldMonitoringEntity m = queue.findById(in.fieldId()).orElseThrow();
        m.setLastSceneDate(r.sceneDate());
        m.setLastSeenDate(later(m.getLastSeenDate(), later(r.latestSeen(), r.sceneDate())));
        m.setLastNdvi(dec(r.ndvi(), 3));
        m.setNextCheckAt(next);
        m.setLastCheckedAt(LocalDateTime.now());
        m.setFailures(0);
        if (events.existsByFieldIdAndSceneDate(in.fieldId(), r.sceneDate())) return false;   // qayta ishlangan surat

        MonitoringEventEntity e = new MonitoringEventEntity();
        e.setFieldId(in.fieldId());
        e.setSceneDate(r.sceneDate());
        e.setRefDate(r.refDate());
        e.setNdvi(dec(r.ndvi(), 3));
        e.setNdviRef(dec(r.ndviRef(), 3));
        e.setDeclinePct(dec(r.declinePct(), 1));
        e.setDeclineSide(r.declineSide());
        e.setImprovePct(dec(r.improvePct(), 1));
        e.setVerdict(verdict);
        e.setMapFileHashId(mapHashId);
        e.setCreatedDate(LocalDateTime.now());
        events.save(e);
        if (verdict != Verdict.DECLINE || !alert) return false;           // bepul dala (paid-only): holat yoziladi, xabar yo'q

        Notification n = Notification.template(in.farmerId(), NotificationCategory.MONITORING, Templates.MONITORING_ALERT)
                .param("fieldId", in.fieldId()).param("fieldName", in.fieldName())
                .param("sceneDate", r.sceneDate().toString()).param("refDate", r.refDate().toString())
                .param("declinePct", r.declinePct()).param("side", r.declineSide()).param("fileHashId", mapHashId)
                .ref("MONITORING_EVENT", e.getId())
                .preview("Monitoring: «" + in.fieldName() + "» " + r.declinePct() + "% sustlashdi (" + r.sceneDate() + ")");
        notifications.enqueue(n);
        return true;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        if (a == null) return b;
        return b == null || a.isAfter(b) ? a : b;
    }

    private static BigDecimal dec(Double v, int scale) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
