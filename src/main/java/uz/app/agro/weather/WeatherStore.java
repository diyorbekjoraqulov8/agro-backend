package uz.app.agro.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.notification.Templates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tranzaksiya chegarasi — job/service'dan alohida bean (ADR #24). Tarmoq (Open-Meteo) bu yerda CHAQIRILMAYDI.
 * Ogohlantirish yozuvi va bildirishnoma — bitta tranzaksiyada (outbox): biri yo'qolsa, ikkinchisi ham yo'q.
 */
@Component
@RequiredArgsConstructor
class WeatherStore {

    /** Issiq to'lqin har kuni emas: oldingi 5 kunda aytilgan bo'lsa — WARNING jim; DANGER (kuchaygan) baribir ketadi */
    static final int HEAT_COOLDOWN_DAYS = 5;

    private final WeatherForecastRepository forecasts;
    private final WeatherAlertRepository alerts;
    private final NotificationService notifications;

    @Transactional
    public void save(List<OpenMeteoClient.CellForecast> fetched, Map<String, WeatherCell> cells) {
        for (OpenMeteoClient.CellForecast f : fetched) {
            WeatherCell c = cells.get(f.cellKey());
            String days = JsonMapper.shared().writeValueAsString(f.days().stream().map(DailyForecast::toMap).toList());
            forecasts.upsert(f.cellKey(), c.lat(), c.lon(), f.timezone(), f.utcOffsetSeconds(), days);
        }
    }

    /**
     * @return true — yangi yoki kuchaygan xavf, xabar navbatga qo'yildi; false — allaqachon aytilgan
     */
    @Transactional
    public boolean alert(long farmerId, String cellKey, WeatherRisk r, List<String> fieldNames) {
        BigDecimal value = BigDecimal.valueOf(r.value()).setScale(1, RoundingMode.HALF_UP);
        WeatherAlertEntity e = alerts.findByFarmerIdAndCellKeyAndKindAndEventDate(farmerId, cellKey, r.kind(), r.date()).orElse(null);
        boolean escalated = e != null;
        if (e != null) {
            // Faqat WARNING → DANGER, atomik (ikki instans bir vaqtda ko'rsa — bittasi yuboradi)
            if (r.level() != WeatherRisk.Level.DANGER || alerts.escalate(e.getId(), value) == 0) return false;
        } else {
            if (r.kind() == WeatherRisk.Kind.HEAT && r.level() == WeatherRisk.Level.WARNING
                    && alerts.existsByFarmerIdAndCellKeyAndKindAndEventDateBetween(farmerId, cellKey, WeatherRisk.Kind.HEAT,
                            r.date().minusDays(HEAT_COOLDOWN_DAYS), r.date().minusDays(1))) {
                return false;
            }
            e = new WeatherAlertEntity();
            e.setFarmerId(farmerId);
            e.setCellKey(cellKey);
            e.setKind(r.kind());
            e.setEventDate(r.date());
            e.setLevel(r.level());
            e.setValue(value);
            e.setCreatedDate(LocalDateTime.now());
            alerts.save(e);                    // IDENTITY — INSERT darhol: UNIQUE poygasi shu yerda chiqadi, xabar yo'q
        }
        notifications.enqueue(Notification.template(farmerId, NotificationCategory.WEATHER, Templates.WEATHER_ALERT)
                .param("kind", r.kind().name()).param("level", r.level().name()).param("date", r.date().toString())
                .param("value", r.value()).param("fields", fieldNames).param("escalated", escalated)
                .ref("WEATHER_ALERT", e.getId())
                .preview("Ob-havo: " + r.kind() + " " + r.level() + " " + r.date() + " (" + Math.round(r.value()) + ")"));
        return true;
    }
}
