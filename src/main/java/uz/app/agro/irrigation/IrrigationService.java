package uz.app.agro.irrigation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.satellite.SatelliteRejectedException;
import uz.app.agro.satellite.SatelliteUnavailableException;
import uz.app.agro.satellite.dto.WaterDtos;

import uz.app.agro.notification.Notification;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Sug'orish eslatmasi mantig'i. @Transactional YO'Q — Python (Open-Meteo/SoilGrids/Sentinel) chaqiruvlari
 * tranzaksiyadan tashqarida; DB — FieldWaterStore'da. Bot uchun metodlar telegramUserId bilan (egalik).
 * @PreAuthorize YO'Q — controller'ga ULANMASIN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrrigationService {

    private static final int NDVI_FRESH_DAYS = 10;     // Kc uchun NDVI shundan eski bo'lsa — yangilanadi

    private final FieldWaterStore store;
    private final SatelliteClient satellite;

    public enum SoilCheck { MATCH, MISMATCH, AUTO_ONLY, UNAVAILABLE }

    public record SoilResult(SoilType farmerChoice, SoilType auto, SoilCheck check) {}

    /**
     * Fermer tuproq turini tanladi (null — "bilmayman"). SoilGrids bilan avtomatik tekshiriladi:
     * fermer javobi ustun (dalani u biladi, xarita 250 m), farq bo'lsa — fermerga aytiladi, admin ko'radi.
     */
    public Optional<SoilResult> setSoil(long tg, long fieldId, SoilType farmerChoice) {
        if (!store.owns(tg, fieldId)) return Optional.empty();
        var ctx = store.context(fieldId).orElse(null);
        if (ctx == null) return Optional.empty();
        WaterDtos.SoilResponse auto = null;
        try {
            auto = satellite.soil(ctx.lat(), ctx.lon());
        } catch (SatelliteRejectedException | SatelliteUnavailableException e) {
            log.warn("field {}: SoilGrids javob bermadi: {}", fieldId, e.getMessage());
        }
        store.saveSoil(fieldId, farmerChoice, auto);
        SoilType autoType = auto == null ? null : SoilType.valueOf(auto.texture());
        SoilCheck check = autoType == null ? SoilCheck.UNAVAILABLE
                : farmerChoice == null ? SoilCheck.AUTO_ONLY
                : farmerChoice == autoType ? SoilCheck.MATCH : SoilCheck.MISMATCH;
        return Optional.of(new SoilResult(farmerChoice, autoType, check));
    }

    /** "Oxirgi marta qachon sug'organsiz?" — daysAgo: 0, 2, 7, 14 */
    public Optional<WaterStatus> setLastIrrigation(long tg, long fieldId, int daysAgo, IrrigationEventEntity.Source source) {
        if (!store.owns(tg, fieldId) || daysAgo < 0 || daysAgo > 60) return Optional.empty();
        var ctx = store.context(fieldId).orElse(null);
        if (ctx == null) return Optional.empty();
        LocalDate today = ctx.water() != null ? ctx.water().localToday() : LocalDate.now();
        store.saveIrrigation(fieldId, today.minusDays(daysAgo), source);
        return compute(fieldId, false);
    }

    public Optional<WaterStatus> toggle(long tg, long fieldId) {
        if (!store.owns(tg, fieldId)) return Optional.empty();
        store.toggle(fieldId);
        return status(fieldId);
    }

    /** Hisoblamasdan — oxirgi natija (dala kartochkasi) */
    public Optional<WaterStatus> status(long tg, long fieldId) {
        return store.owns(tg, fieldId) ? status(fieldId) : Optional.empty();
    }

    Optional<WaterStatus> status(long fieldId) {
        return store.context(fieldId).map(ctx -> toStatus(ctx, null));
    }

    /**
     * Python balansini hisoblaydi va saqlaydi. Kc — dalaning NDVI'sidan: saqlangan yoki so'nggi
     * tahlilniki (≤ 10 kun), aks holda refreshNdvi=true bo'lsa Sentinel'dan yangisi; bo'lmasa — eskisi / 0.9.
     */
    public Optional<WaterStatus> compute(long fieldId, boolean refreshNdvi) {
        var ctx = store.context(fieldId).orElse(null);
        if (ctx == null || ctx.water() == null || ctx.water().getLastIrrigatedOn() == null) return Optional.empty();
        FieldWaterEntity w = ctx.water();
        LocalDate today = w.localToday();

        Double ndvi = w.getNdvi() != null ? w.getNdvi().doubleValue() : null;
        LocalDate ndviOn = w.getNdviOn();
        if (ctx.analysisNdviOn() != null && (ndviOn == null || ctx.analysisNdviOn().isAfter(ndviOn))) {
            ndvi = ctx.analysisNdvi();
            ndviOn = ctx.analysisNdviOn();
        }
        if (refreshNdvi && (ndviOn == null || ndviOn.isBefore(today.minusDays(NDVI_FRESH_DAYS)))) {
            try {
                var fresh = satellite.latestNdvi(ctx.geometry());
                ndvi = fresh.ndvi();
                ndviOn = fresh.date();
            } catch (SatelliteRejectedException | SatelliteUnavailableException e) {
                log.info("field {}: yangi NDVI yo'q ({}), eskisi ishlatiladi", fieldId, e.getMessage());
            }
        }

        CropWater cw = CropWater.of(ctx.crop());
        SoilType soil = w.effectiveSoil();
        var b = satellite.waterBalance(new WaterDtos.BalanceRequest(ctx.lat(), ctx.lon(), w.getLastIrrigatedOn(),
                CropWater.kcFromNdvi(ndvi), cw.tawMm(soil), cw.rawMm(soil)));
        store.saveComputation(fieldId, b, ndvi, ndviOn);
        return store.context(fieldId).map(c -> toStatus(c, b));
    }

    /** Eslatma navbatga va "eslatildi" belgisi — bitta tranzaksiyada (biri qolib ketsa takror yoki yo'qolgan eslatma bo'lardi) */
    void remind(long fieldId, LocalDate day, FieldWaterEntity.ReminderKind kind, Notification n) {
        store.saveReminder(fieldId, day, kind, n);
    }

    void markChecked(long fieldId, LocalDate day) {
        store.saveChecked(fieldId, day);
    }

    private static WaterStatus toStatus(FieldWaterStore.Context ctx, WaterDtos.BalanceResponse b) {
        FieldWaterEntity w = ctx.water();
        if (w == null) {
            return new WaterStatus(ctx.fieldId(), ctx.fieldName(), ctx.tg(), ctx.chatId(), ctx.language(), false, true,
                    null, null, SoilType.LOAM, null, LocalDate.now(), null, null, false, null, null, ctx.analysisNdvi());
        }
        LocalDate today = b != null ? b.localDate() : w.localToday();
        LocalDate next = w.getNextIrrigationOn();
        Integer daysUntil = b != null ? b.daysUntil()
                : next == null ? null : (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, next));
        boolean due = b != null ? b.due() : next != null && !next.isAfter(today);
        return new WaterStatus(ctx.fieldId(), ctx.fieldName(), ctx.tg(), ctx.chatId(), ctx.language(),
                w.getLastIrrigatedOn() != null, w.isRemindersEnabled(), w.getSoilType(), w.getSoilTypeAuto(), w.effectiveSoil(),
                w.getLastIrrigatedOn(), today, next, daysUntil, due,
                w.getDepletionPct() == null ? null : w.getDepletionPct().doubleValue(),
                b == null ? null : b.rainNext3dMm(),
                w.getNdvi() != null ? Double.valueOf(w.getNdvi().doubleValue()) : ctx.analysisNdvi());
    }
}
