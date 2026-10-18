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

    /**
     * Dala kartasidagi "Hozirgi holat" — monitoring yozgan oxirgi surat (kartaga darhol, Python chaqirilmaydi).
     * enabled — monitoring yoqilgan; alerts — fermerga ogohlantirish boradi (pullik yoki paid-only o'chiq);
     * trendPct — ~2 hafta oldingi suratga nisbatan yashillik o'zgarishi (null — solishtiradigan surat yo'q)
     */
    public record CardStatus(boolean enabled, boolean alerts, LocalDate sceneDate, BigDecimal ndvi, Verdict verdict,
                             Integer trendPct, BigDecimal declinePct) {}

    /** Trend: oxirgi suratdan kamida shuncha kun oldingi surat bilan (Sentinel-2 ~5 kun, 2–3 surat oldin) */
    private static final int TREND_MIN_DAYS = 10;
    private static final int TREND_MAX_DAYS = 30;

    private final FieldRepository fields;
    private final MonitoringEventRepository events;
    private final PlanService plans;
    private final SettingsService settings;

    public Optional<CardStatus> card(long telegramUserId, long fieldId) {
        var field = fields.findById(fieldId).filter(f -> f.getFarmer().getTelegramUserId() == telegramUserId).orElse(null);
        if (field == null) return Optional.empty();
        if (!settings.getBool(Setting.MONITORING_ENABLED)) return Optional.of(new CardStatus(false, false, null, null, null, null, null));
        boolean alerts = !settings.getBool(Setting.MONITORING_PAID_ONLY) || plans.paidOn(field.getFarmer().getId(), LocalDate.now());
        var last = events.findFirstByFieldIdOrderBySceneDateDesc(fieldId).orElse(null);
        if (last == null) return Optional.of(new CardStatus(true, alerts, null, null, null, null, null));
        Integer trend = events.findFirstByFieldIdAndSceneDateLessThanEqualOrderBySceneDateDesc(fieldId, last.getSceneDate().minusDays(TREND_MIN_DAYS))
                .filter(o -> !o.getSceneDate().isBefore(last.getSceneDate().minusDays(TREND_MAX_DAYS)))
                .filter(o -> o.getNdvi().doubleValue() >= 0.1)
                .map(o -> (int) Math.round((last.getNdvi().doubleValue() - o.getNdvi().doubleValue()) / o.getNdvi().doubleValue() * 100))
                .orElse(null);
        return Optional.of(new CardStatus(true, alerts, last.getSceneDate(), last.getNdvi(), last.getVerdict(), trend, last.getDeclinePct()));
    }
}
