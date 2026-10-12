package uz.app.agro.irrigation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.Templates;

import java.time.OffsetDateTime;
import java.util.Map;
import java.time.ZoneOffset;

/**
 * Har soat: dala MAHALLIY vaqti bo'yicha soat {reminder-hour} DAN KEYIN va bugun hali tekshirilmagan dalalar
 * uchun qaror (global — O'zbekiston va Argentina bitta jadvalda). "Aynan soat 7" emas: bir zonadagi dalalar
 * ko'p bo'lsa aylanish 8:00 dan oshadi va qolganlari o'sha kuni tushib qolardi. Qaror qabul qilingach
 * (yuborildi yoki yuboradigan narsa yo'q) last_checked_on — Python kuniga bir marta chaqiriladi.
 * Yuborish/hisob xatosi — belgilanmaydi, keyingi soatda qayta urinadi.
 * Qoida (loyiha egasi qarori — "yaqinlashganda"):
 *   DUE    — bugun sug'orish kerak (har kuni, "✅ Sug'ordim" bosilguncha)
 *   SOON   — 1–2 kun qoldi (bir marta)
 *   STATUS — 7 kunda bir marta qisqa holat
 * Ekin ko'rinmasa (NDVI < 0.2 — shudgor, qish) — yuborilmaydi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "irrigation.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class IrrigationReminderJob {

    private final FieldWaterRepository waterRepository;
    private final IrrigationService service;

    @Value("${irrigation.reminders.hour:7}")
    private int reminderHour;

    @Scheduled(cron = "${irrigation.reminders.cron:0 5 * * * *}")
    public void tick() {
        for (Long fieldId : waterRepository.activeFieldIds()) {
            try {
                MDC.put("requestId", "water-" + fieldId);
                runFor(fieldId, false);
            } catch (RuntimeException e) {
                log.warn("field {}: eslatma hisoblanmadi: {}", fieldId, e.getMessage());
            } finally {
                MDC.clear();
            }
        }
    }

    /** @param ignoreHour — sinov/qo'lda: soat va "bugun yuborilgan"ni tekshirmaydi */
    public FieldWaterEntity.ReminderKind runFor(long fieldId, boolean ignoreHour) {
        FieldWaterEntity w = waterRepository.findById(fieldId).orElse(null);
        if (w == null || !w.isRemindersEnabled() || w.getLastIrrigatedOn() == null) return null;
        if (!ignoreHour) {
            if (w.getUtcOffsetSeconds() == null) {
                // Hali hisoblanmagan — vaqt zonasini Open-Meteo beradi (keyin soat tekshiriladi)
                service.compute(fieldId, false);
                w = waterRepository.findById(fieldId).orElse(w);
                if (w.getUtcOffsetSeconds() == null) return null;
            }
            if (OffsetDateTime.now(ZoneOffset.ofTotalSeconds(w.getUtcOffsetSeconds())).getHour() < reminderHour) return null;
            if (w.localToday().equals(w.getLastCheckedOn())) return null;       // bugun qaror qilingan
        }

        WaterStatus s = service.compute(fieldId, true).orElse(null);
        if (s == null) return null;                                             // hisob bo'lmadi — keyingi soatda
        FieldWaterEntity.ReminderKind kind = s.noCrop() ? null : decide(s, w);
        if (kind == null) {
            if (!ignoreHour) service.markChecked(fieldId, s.localToday());
            return null;
        }
        // Outbox: navbatga qo'yildi — "yuborilgan" deb belgilanadi (yetkazishni dispatcher kafolatlaydi, xato bo'lsa qayta uriniladi)
        long farmerId = waterRepository.farmerIdOf(fieldId);
        @SuppressWarnings("unchecked")
        Map<String, Object> status = JsonMapper.shared().convertValue(s, Map.class);
        service.remind(fieldId, s.localToday(), kind, Notification.template(farmerId, NotificationCategory.IRRIGATION, Templates.IRRIGATION_REMINDER)
                .param("status", status).param("kind", kind.name())
                .preview("Sug'orish (" + kind + ") — «" + s.fieldName() + "», keyingisi " + s.nextIrrigationOn()));
        log.info("field {}: {} navbatga qo'yildi (keyingi sug'orish {}, {} kun)", fieldId, kind, s.nextIrrigationOn(), s.daysUntil());
        return kind;
    }

    static FieldWaterEntity.ReminderKind decide(WaterStatus s, FieldWaterEntity w) {
        if (s.due()) return FieldWaterEntity.ReminderKind.DUE;
        if (s.daysUntil() != null && s.daysUntil() <= 2) {
            boolean alreadySoon = w.getLastReminderKind() == FieldWaterEntity.ReminderKind.SOON && w.getLastReminderOn() != null
                    && !w.getLastReminderOn().isBefore(s.localToday().minusDays(2));
            return alreadySoon ? null : FieldWaterEntity.ReminderKind.SOON;
        }
        if (w.getLastReminderOn() == null || !w.getLastReminderOn().isAfter(s.localToday().minusDays(7))) {
            return FieldWaterEntity.ReminderKind.STATUS;
        }
        return null;
    }
}
