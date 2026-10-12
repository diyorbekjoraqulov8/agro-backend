package uz.app.agro.irrigation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.analysis.AnalysisJob;
import uz.app.agro.analysis.AnalysisJobRepository;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.satellite.dto.WaterDtos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tranzaksiya chegaralari ALOHIDA bean'da (ADR #24): IrrigationService Python'ga (Open-Meteo,
 * SoilGrids, Sentinel) tranzaksiyadan TASHQARIDA murojaat qiladi, DB'ga faqat shu yerda.
 */
@Component
@RequiredArgsConstructor
class FieldWaterStore {

    private final FieldRepository fieldRepository;
    private final NotificationService notifications;
    private final FieldWaterRepository waterRepository;
    private final IrrigationEventRepository eventRepository;
    private final AnalysisJobRepository jobRepository;

    /** Hisob uchun kerakli hamma narsa (dala o'chirilgan — empty) */
    record Context(long fieldId, String fieldName, uz.app.agro.field.Crop crop, double lat, double lon,
                   Map<String, Object> geometry, long tg, long chatId, String language,
                   FieldWaterEntity water, Double analysisNdvi, LocalDate analysisNdviOn) {}

    @Transactional(readOnly = true)
    public Optional<Context> context(long fieldId) {
        return fieldRepository.findById(fieldId).filter(f -> f.getCentroidLat() != null).map(f -> {
            var farmer = f.getFarmer();
            FieldWaterEntity w = waterRepository.findById(fieldId).orElse(null);
            // So'nggi tahlilning oxirgi kuzatuvi — Kc uchun (agar yangi bo'lsa)
            Double ndvi = null;
            LocalDate ndviOn = null;
            var job = jobRepository.findTopByFieldIdAndStatusOrderByIdDesc(fieldId, AnalysisJob.Status.DONE);
            if (job.isPresent() && job.get().getResult() != null && job.get().getResult().get("timeseries") instanceof List<?> ts && !ts.isEmpty()
                    && ts.getLast() instanceof Map<?, ?> last && last.get("ndvi") instanceof Number n) {
                ndvi = n.doubleValue();
                ndviOn = LocalDate.parse(String.valueOf(last.get("date")));
            }
            return new Context(f.getId(), f.getName(), f.getCrop(), f.getCentroidLat().doubleValue(), f.getCentroidLon().doubleValue(),
                    new HashMap<>(f.getBoundaryGeojson()), farmer.getTelegramUserId(), farmer.getChatId(), farmer.getLanguage(),
                    w, ndvi, ndviOn);
        });
    }

    /** Egalik: dala SHU Telegram foydalanuvchisiniki (callback_data ishonchsiz — IDOR) */
    @Transactional(readOnly = true)
    public boolean owns(long telegramUserId, long fieldId) {
        return fieldRepository.findById(fieldId).map(f -> f.getFarmer().getTelegramUserId() == telegramUserId).orElse(false);
    }

    @Transactional
    public void saveSoil(long fieldId, SoilType farmerChoice, WaterDtos.SoilResponse auto) {
        FieldWaterEntity w = getOrCreate(fieldId);
        w.setSoilType(farmerChoice);
        if (auto != null) {
            w.setSoilTypeAuto(SoilType.valueOf(auto.texture()));
            w.setSandPct(BigDecimal.valueOf(auto.sandPct()).setScale(1, RoundingMode.HALF_UP));
            w.setClayPct(BigDecimal.valueOf(auto.clayPct()).setScale(1, RoundingMode.HALF_UP));
        }
    }

    @Transactional
    public void saveIrrigation(long fieldId, LocalDate day, IrrigationEventEntity.Source source) {
        FieldWaterEntity w = getOrCreate(fieldId);
        w.setLastIrrigatedOn(day);
        w.setRemindersEnabled(true);
        // Sug'orildi — keyingi eslatma yangi tsikl bo'yicha (bugun "DUE" yuborilgan bo'lsa ham)
        w.setLastReminderKind(null);
        IrrigationEventEntity e = new IrrigationEventEntity();
        e.setFieldId(fieldId);
        e.setIrrigatedOn(day);
        e.setSource(source);
        eventRepository.save(e);
    }

    @Transactional
    public void saveComputation(long fieldId, WaterDtos.BalanceResponse b, Double ndvi, LocalDate ndviOn) {
        FieldWaterEntity w = getOrCreate(fieldId);
        w.setTimezone(b.timezone());
        w.setUtcOffsetSeconds(b.utcOffsetSeconds());
        w.setNextIrrigationOn(b.irrigateOn());
        w.setDepletionPct(BigDecimal.valueOf(b.depletionPct()).setScale(1, RoundingMode.HALF_UP));
        w.setComputedAt(LocalDateTime.now());
        if (ndvi != null) {
            w.setNdvi(BigDecimal.valueOf(ndvi).setScale(3, RoundingMode.HALF_UP));
            w.setNdviOn(ndviOn);
        }
    }

    @Transactional
    public void saveReminder(long fieldId, LocalDate localDay, FieldWaterEntity.ReminderKind kind, Notification n) {
        notifications.enqueue(n);                    // outbox — shu tranzaksiyada
        FieldWaterEntity w = getOrCreate(fieldId);
        w.setLastReminderOn(localDay);
        w.setLastReminderKind(kind);
        w.setLastCheckedOn(localDay);
    }

    @Transactional
    public void saveChecked(long fieldId, LocalDate localDay) {
        getOrCreate(fieldId).setLastCheckedOn(localDay);
    }

    @Transactional
    public boolean toggle(long fieldId) {
        FieldWaterEntity w = getOrCreate(fieldId);
        w.setRemindersEnabled(!w.isRemindersEnabled());
        return w.isRemindersEnabled();
    }

    @Transactional(readOnly = true)
    public List<IrrigationEventEntity> events(long fieldId) {
        return eventRepository.findTop20ByFieldIdOrderByIrrigatedOnDescIdDesc(fieldId);
    }

    private FieldWaterEntity getOrCreate(long fieldId) {
        return waterRepository.findById(fieldId).orElseGet(() -> {
            FieldWaterEntity w = new FieldWaterEntity();
            w.setFieldId(fieldId);
            return waterRepository.save(w);
        });
    }
}
