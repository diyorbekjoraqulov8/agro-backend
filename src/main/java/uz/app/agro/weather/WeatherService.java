package uz.app.agro.weather;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.app.agro.problem.ProblemRecorder;
import uz.app.agro.problem.ProblemSource;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Prognoz keshi (kataklar) va bot "🌦 Ob-havo". @PreAuthorize YO'Q — bot va job chaqiradi, controller'ga ULANMASIN.
 * Klass darajasida @Transactional YO'Q (ataylab): Open-Meteo chaqiriladi — tarmoq tranzaksiya ichida bo'lmasin;
 * yozish — WeatherStore.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    /** Bitta xabarda ko'pi bilan nechta hudud (fermer dalalari uzoq-uzoqda bo'lsa) */
    static final int MAX_CELLS = 3;

    public record CellView(List<String> fieldNames, boolean southern, List<DailyForecast> days) {}

    private final WeatherForecastRepository forecasts;
    private final OpenMeteoClient client;
    private final WeatherStore store;
    private final SettingsService settings;
    private final ProblemRecorder problems;

    /**
     * Ko'p dalali hudud birinchi. Bot thread'i bitta (long polling) — barcha fermerlarni to'smaslik uchun faqat
     * keshi UMUMAN yo'q katak olinadi; eskirganini job har soat yangilaydi.
     */
    public List<CellView> forecastFor(long telegramUserId) {
        Map<String, WeatherCell> cells = new LinkedHashMap<>();
        Map<String, List<String>> names = new HashMap<>();
        for (var p : forecasts.fieldPointsOf(telegramUserId)) {
            WeatherCell c = WeatherCell.of(p.getLat(), p.getLon());
            cells.putIfAbsent(c.key(), c);
            names.computeIfAbsent(c.key(), k -> new ArrayList<>()).add(p.getFieldName());
        }
        Map<String, WeatherCell> top = new LinkedHashMap<>();
        cells.keySet().stream().sorted(Comparator.comparingInt((String k) -> -names.get(k).size()))
                .limit(MAX_CELLS).forEach(k -> top.put(k, cells.get(k)));
        Map<String, WeatherForecastEntity> cached = refresh(top, null);
        return top.keySet().stream().filter(cached::containsKey).map(k -> {
            WeatherForecastEntity f = cached.get(k);
            return new CellView(names.get(k), f.getLat().signum() < 0, f.forecast());
        }).toList();
    }

    /**
     * Kataklar prognozi: keshda yo'g'i (va staleBefore berilsa — undan eskisi) Open-Meteo'dan, 50 tadan.
     * Bitta partiya xatosi qolganini to'xtatmaydi — eski kesh qaytadi. @return katak → prognoz (bori)
     */
    public Map<String, WeatherForecastEntity> refresh(Map<String, WeatherCell> cells, LocalDateTime staleBefore) {
        Map<String, WeatherForecastEntity> cached = new HashMap<>();
        forecasts.findAllById(cells.keySet()).forEach(f -> cached.put(f.getCellKey(), f));
        List<WeatherCell> due = cells.values().stream()
                .filter(c -> !cached.containsKey(c.key()) || (staleBefore != null && cached.get(c.key()).getFetchedAt().isBefore(staleBefore)))
                .sorted(Comparator.comparing(WeatherCell::key)).toList();
        for (int i = 0; i < due.size(); i += OpenMeteoClient.BATCH) {
            List<WeatherCell> batch = due.subList(i, Math.min(due.size(), i + OpenMeteoClient.BATCH));
            try {
                store.save(client.fetch(batch), cells);
                forecasts.findAllById(batch.stream().map(WeatherCell::key).toList()).forEach(f -> cached.put(f.getCellKey(), f));
            } catch (RuntimeException e) {         // WeatherUnavailable, buzuq JSON, DB — keyingi soatda qayta
                log.warn("ob-havo prognozi olinmadi ({} katak): {}", batch.size(), e.getMessage());
                problems.error(ProblemSource.WEATHER, "Ob-havo prognozi olinmadi (Open-Meteo): " + e.getMessage()).cause(e).record();
            }
        }
        return cached;
    }

    public WeatherRisk.Thresholds thresholds() {
        return new WeatherRisk.Thresholds(settings.getInt(Setting.WEATHER_FROST_C), settings.getInt(Setting.WEATHER_HEAT_C),
                settings.getInt(Setting.WEATHER_WIND_MS), settings.getInt(Setting.WEATHER_RAIN_MM));
    }
}
