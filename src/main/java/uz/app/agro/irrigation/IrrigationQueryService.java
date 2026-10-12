package uz.app.agro.irrigation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.rbac.Permissions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Admin panel: dala sug'orish holati (faqat o'qish). */
@Service
@RequiredArgsConstructor
public class IrrigationQueryService {

    private final FieldWaterStore store;

    public record EventView(LocalDate irrigatedOn, IrrigationEventEntity.Source source) {}

    public record WaterView(boolean configured, boolean enabled, SoilType soil, SoilType soilAuto, SoilType soilUsed,
                            boolean soilMismatch, BigDecimal sandPct, BigDecimal clayPct, LocalDate lastIrrigatedOn,
                            LocalDate nextIrrigationOn, Double depletionPct, Double ndvi, LocalDate ndviOn, String timezone,
                            LocalDate lastReminderOn, String lastReminderKind, List<EventView> events) {}

    @PreAuthorize(Permissions.CAN_READ_FIELD)
    public WaterView get(Long fieldId) {
        var ctx = store.context(fieldId).orElseThrow(() -> new NotFoundException("Dala", fieldId));
        FieldWaterEntity w = ctx.water();
        if (w == null) {
            return new WaterView(false, false, null, null, null, false, null, null, null, null, null,
                    ctx.analysisNdvi(), ctx.analysisNdviOn(), null, null, null, List.of());
        }
        return new WaterView(w.getLastIrrigatedOn() != null, w.isRemindersEnabled(), w.getSoilType(), w.getSoilTypeAuto(),
                w.effectiveSoil(), w.getSoilType() != null && w.getSoilTypeAuto() != null && w.getSoilType() != w.getSoilTypeAuto(),
                w.getSandPct(), w.getClayPct(), w.getLastIrrigatedOn(), w.getNextIrrigationOn(),
                w.getDepletionPct() == null ? null : w.getDepletionPct().doubleValue(),
                w.getNdvi() == null ? ctx.analysisNdvi() : w.getNdvi().doubleValue(), w.getNdvi() == null ? ctx.analysisNdviOn() : w.getNdviOn(),
                w.getTimezone(), w.getLastReminderOn(), w.getLastReminderKind() == null ? null : w.getLastReminderKind().name(),
                store.events(fieldId).stream().map(e -> new EventView(e.getIrrigatedOn(), e.getSource())).toList());
    }
}
