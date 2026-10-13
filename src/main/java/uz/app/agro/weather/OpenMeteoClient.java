package uz.app.agro.weather;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Open-Meteo forecast: bitta so'rovda bir nechta katak (vergul bilan), 5 kunlik kunlik qiymatlar, dala vaqt zonasida.
 * Tarmoq — tranzaksiyadan TASHQARIDA chaqiriladi (WeatherAlertJob / WeatherService).
 */
@Slf4j
@Component
public class OpenMeteoClient {

    /** Bitta so'rovdagi kataklar soni — URL uzunligi va javob hajmi chegarasi */
    static final int BATCH = 50;
    static final int DAYS = 5;
    private static final String DAILY = "temperature_2m_min,temperature_2m_max,wind_gusts_10m_max,precipitation_sum,weather_code";

    public record CellForecast(String cellKey, String timezone, Integer utcOffsetSeconds, List<DailyForecast> days) {}

    private final RestClient rest;
    private final WeatherProperties props;

    public OpenMeteoClient(WeatherProperties props) {
        this.props = props;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    /** @return kataklar tartibida; xato — WeatherUnavailableException (job keyingi soatda qayta uradi) */
    public List<CellForecast> fetch(List<WeatherCell> cells) {
        if (cells.isEmpty()) return List.of();
        if (cells.size() > BATCH) throw new IllegalArgumentException("ko'pi bilan " + BATCH + " katak");
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(props.forecastUrl())
                .queryParam("latitude", cells.stream().map(c -> c.lat().toPlainString()).collect(Collectors.joining(",")))
                .queryParam("longitude", cells.stream().map(c -> c.lon().toPlainString()).collect(Collectors.joining(",")))
                .queryParam("daily", DAILY)
                .queryParam("forecast_days", DAYS)
                .queryParam("timezone", "auto")
                .queryParam("wind_speed_unit", "ms");
        if (props.hasApiKey()) uri.queryParam("apikey", props.apiKey());
        String body;
        try {
            body = rest.get().uri(uri.build().toUri()).retrieve().body(String.class);
        } catch (RestClientException e) {
            // URL'da kalit bo'lishi mumkin — log'ga faqat xato turi
            throw new WeatherUnavailableException("Open-Meteo: " + e.getClass().getSimpleName() + " " + firstLine(e.getMessage()));
        }
        try {
            JsonNode root = JsonMapper.shared().readTree(body);
            // Bitta koordinata — obyekt, bir nechta — massiv
            List<JsonNode> items = new ArrayList<>();
            if (root.isArray()) root.forEach(items::add);
            else items.add(root);
            if (items.size() != cells.size()) {
                throw new WeatherUnavailableException("Open-Meteo: " + cells.size() + " katak so'raldi, " + items.size() + " keldi");
            }
            List<CellForecast> out = new ArrayList<>();
            for (int i = 0; i < cells.size(); i++) out.add(parse(cells.get(i).key(), items.get(i)));
            return out;
        } catch (WeatherUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {                       // buzuq JSON, sana formati — tashqi ma'lumot
            throw new WeatherUnavailableException("Open-Meteo javobi o'qilmadi: " + e.getClass().getSimpleName());
        }
    }

    private static CellForecast parse(String cellKey, JsonNode n) {
        JsonNode d = n.path("daily");
        JsonNode time = d.path("time");
        List<DailyForecast> days = new ArrayList<>();
        for (int i = 0; i < time.size(); i++) {
            days.add(new DailyForecast(LocalDate.parse(time.get(i).asString()),
                    num(d, "temperature_2m_min", i), num(d, "temperature_2m_max", i), num(d, "wind_gusts_10m_max", i),
                    num(d, "precipitation_sum", i), d.path("weather_code").path(i).isNumber() ? d.path("weather_code").get(i).asInt() : null));
        }
        String tz = n.path("timezone").isString() ? n.path("timezone").asString() : null;
        Integer offset = n.path("utc_offset_seconds").isNumber() ? n.path("utc_offset_seconds").asInt() : null;
        return new CellForecast(cellKey, tz, offset, days);
    }

    private static Double num(JsonNode daily, String field, int i) {
        JsonNode v = daily.path(field).path(i);
        return v.isNumber() ? v.asDouble() : null;
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return (nl < 0 ? s : s.substring(0, nl)).replaceAll("apikey=[^&\\s]*", "apikey=***");
    }
}
