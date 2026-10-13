package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.notification.Templates;
import uz.app.agro.satellite.dto.WaterDtos.MonitorResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tranzaksiya chegarasi — job'dan alohida bean (ADR #24). Python (tarmoq) bu yerda CHAQIRILMAYDI.
 * Hodisa yozuvi va fermerga xabar — bitta tranzaksiyada (outbox).
 */
@Component
@RequiredArgsConstructor
class MonitoringStore {

    record Input(long fieldId, String fieldName, long farmerId, Map<String, Object> geometry, LocalDate since) {}

    private final FieldMonitoringRepository queue;
    private final MonitoringEventRepository events;
    private final FieldRepository fields;
    private final NotificationService notifications;

    @Transactional
    public int enroll(boolean paidOnly) {
        return queue.enroll(paidOnly);
    }

    /** Python'ga yuboriladigan narsa — LAZY tranzaksiya ichida o'qiladi. Dala o'chirilgan — bo'sh */
    @Transactional(readOnly = true)
    public Optional<Input> input(long fieldId) {
        FieldEntity f = fields.findById(fieldId).orElse(null);
        FieldMonitoringEntity m = queue.findById(fieldId).orElse(null);
        if (f == null || m == null) return Optional.empty();
        return Optional.of(new Input(fieldId, f.getName(), f.getFarmer().getId(), new HashMap<>(f.getBoundaryGeojson()), m.getLastSceneDate()));
    }

    /** Yangi surat yo'q (yoki dala ustida bulut) — keyingi tekshiruv vaqti */
    @Transactional
    public void reschedule(long fieldId, LocalDateTime next, boolean failed) {
        queue.findById(fieldId).ifPresent(m -> {
            m.setNextCheckAt(next);
            m.setLastCheckedAt(LocalDateTime.now());
            m.setFailures(failed ? m.getFailures() + 1 : 0);
        });
    }

    /** @return true — fermerga xabar navbatga qo'yildi */
    @Transactional
    public boolean record(Input in, MonitorResponse r, Verdict verdict, String mapHashId, LocalDateTime next) {
        FieldMonitoringEntity m = queue.findById(in.fieldId()).orElseThrow();
        m.setLastSceneDate(r.sceneDate());
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
        if (verdict != Verdict.DECLINE) return false;

        Notification n = Notification.template(in.farmerId(), NotificationCategory.MONITORING, Templates.MONITORING_ALERT)
                .param("fieldId", in.fieldId()).param("fieldName", in.fieldName())
                .param("sceneDate", r.sceneDate().toString()).param("refDate", r.refDate().toString())
                .param("declinePct", r.declinePct()).param("side", r.declineSide()).param("fileHashId", mapHashId)
                .ref("MONITORING_EVENT", e.getId())
                .preview("Monitoring: «" + in.fieldName() + "» " + r.declinePct() + "% sustlashdi (" + r.sceneDate() + ")");
        notifications.enqueue(n);
        return true;
    }

    private static BigDecimal dec(Double v, int scale) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
