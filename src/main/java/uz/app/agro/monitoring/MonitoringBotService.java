package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.billing.PlanService;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Bot dala kartochkasi uchun holat. @PreAuthorize YO'Q — bot chaqiradi, controller'ga ULANMASIN.
 * Egalik tekshiriladi (telegramUserId) — callback_data ishonchsiz.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitoringBotService {

    public enum Mode { OFF, PRO_ONLY, ON }

    /** last — oxirgi surat natijasi (null — hali surat ko'rilmagan) */
    public record CardStatus(Mode mode, LocalDate sceneDate, Verdict verdict, BigDecimal declinePct) {}

    private final FieldRepository fields;
    private final MonitoringEventRepository events;
    private final PlanService plans;
    private final SettingsService settings;

    public Optional<CardStatus> card(long telegramUserId, long fieldId) {
        var field = fields.findById(fieldId).filter(f -> f.getFarmer().getTelegramUserId() == telegramUserId).orElse(null);
        if (field == null) return Optional.empty();
        if (!settings.getBool(Setting.MONITORING_ENABLED)) return Optional.of(new CardStatus(Mode.OFF, null, null, null));
        if (settings.getBool(Setting.MONITORING_PAID_ONLY) && !plans.effective(field.getFarmer().getId(), LocalDate.now()).paid()) {
            return Optional.of(new CardStatus(Mode.PRO_ONLY, null, null, null));
        }
        return Optional.of(events.findFirstByFieldIdOrderBySceneDateDesc(fieldId)
                .map(e -> new CardStatus(Mode.ON, e.getSceneDate(), e.getVerdict(), e.getDeclinePct()))
                .orElse(new CardStatus(Mode.ON, null, null, null)));
    }
}
