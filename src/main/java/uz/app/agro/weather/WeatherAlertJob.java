package uz.app.agro.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Har soat (har soniya EMAS — modellar 1–6 soatda yangilanadi, Open-Meteo bepul limiti ~10 000/kun):
 *   1. dalalar kataklarga (0.1°) guruhlanadi;
 *   2. eskirgan (≥ refresh-after, default 3 soat) kataklar — Open-Meteo'dan, 50 tadan bitta so'rovda;
 *   3. har katak prognozi chegaralar bilan solishtiriladi (WeatherRisk); yangi yoki kuchaygan har xavf
 *      (tur × sana) — fermerga alohida xabar, shu katakdagi barcha dalalari bilan.
 *      Tunda (quiet-from..quiet-to, dala vaqti) yuborilmaydi — ertalab ketadi.
 * weather.enabled=false — faqat shu job o'chadi; botdagi "🌦 Ob-havo" ishlayveradi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "weather.enabled", havingValue = "true", matchIfMissing = true)
class WeatherAlertJob {

    /** Shundan eski prognoz bilan ogohlantirmaymiz (Open-Meteo bir necha kun ishlamasa — eski xavf yuborilmasin) */
    static final Duration MAX_ALERT_AGE = Duration.ofHours(12);

    private final WeatherForecastRepository forecasts;
    private final WeatherService weather;
    private final WeatherStore store;
    private final SettingsService settings;
    private final WeatherProperties props;

    @Scheduled(cron = "${weather.cron:0 20 * * * *}")
    public void tick() {
        try {
            MDC.put("requestId", "weather");
            run();
        } catch (RuntimeException e) {
            log.error("ob-havo job'i to'xtadi", e);
        } finally {
            MDC.clear();
        }
    }

    void run() {
        // katak → fermer → dala nomlari
        Map<String, WeatherCell> cells = new HashMap<>();
        Map<String, Map<Long, List<String>>> farmersByCell = new HashMap<>();
        for (var p : forecasts.fieldPoints()) {
            WeatherCell c = WeatherCell.of(p.getLat(), p.getLon());
            cells.putIfAbsent(c.key(), c);
            farmersByCell.computeIfAbsent(c.key(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(p.getFarmerId(), k -> new ArrayList<>()).add(p.getFieldName());
        }
        if (cells.isEmpty()) return;
        Map<String, WeatherForecastEntity> cached = weather.refresh(cells, LocalDateTime.now().minus(props.refreshAfter()));

        if (!settings.getBool(Setting.WEATHER_ALERTS_ENABLED)) return;
        WeatherRisk.Thresholds t = weather.thresholds();
        LocalDateTime freshAfter = LocalDateTime.now().minus(MAX_ALERT_AGE);
        int sent = 0;
        for (var en : farmersByCell.entrySet()) {
            WeatherForecastEntity f = cached.get(en.getKey());
            if (f == null || f.getUtcOffsetSeconds() == null || f.getFetchedAt().isBefore(freshAfter)) continue;
            OffsetDateTime local = OffsetDateTime.now(ZoneOffset.ofTotalSeconds(f.getUtcOffsetSeconds()));
            if (quiet(local.getHour())) continue;                       // ertalab qayta tekshiriladi
            List<WeatherRisk> risks = WeatherRisk.detect(f.forecast(), local.toLocalDate(), local.getHour(),
                    f.getLat().signum() < 0, t);
            for (WeatherRisk r : risks) {
                for (var farmer : en.getValue().entrySet()) {
                    try {
                        if (store.alert(farmer.getKey(), en.getKey(), r, farmer.getValue())) sent++;
                    } catch (DataIntegrityViolationException e) {
                        // Ikkinchi instans shu xavfni bir lahza oldin yozdi (UNIQUE) — takror yuborilmaydi
                        log.debug("ob-havo ogohlantirishi allaqachon bor: farmer {} {}", farmer.getKey(), r);
                    } catch (RuntimeException e) {
                        log.warn("ob-havo ogohlantirishi yozilmadi: farmer {} {}: {}", farmer.getKey(), r, e.toString());
                    }
                }
            }
        }
        if (sent > 0) log.info("ob-havo: {} ta ogohlantirish navbatga qo'yildi ({} katak)", sent, cells.size());
    }

    private boolean quiet(int hour) {
        int from = props.quietFrom(), to = props.quietTo();
        return from > to ? hour >= from || hour < to : hour >= from && hour < to;
    }
}
