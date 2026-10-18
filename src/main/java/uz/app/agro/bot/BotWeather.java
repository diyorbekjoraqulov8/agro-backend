package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.field.FieldBotService;
import uz.app.agro.notification.NotificationChannel.Recipient;
import uz.app.agro.weather.DailyForecast;
import uz.app.agro.weather.WeatherRisk;
import uz.app.agro.weather.WeatherService;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bo'lim "🌦 Ob-havo" (5 kunlik prognoz, xavfli kun — ⚠️) va ogohlantirish xabari (Templates.WEATHER_ALERT —
 * BotNotificationChannel chaqiradi, fermer tilida).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotWeather {

    private static final int MAX_NAMES = 4;

    private final BotSender sender;
    private final BotMessages messages;
    private final WeatherService weather;
    private final FieldBotService fields;

    public void showForecast(long tg, long chatId, String lang) {
        List<WeatherService.CellView> cells = weather.forecastFor(tg);
        if (cells.isEmpty()) {
            sender.send(chatId, messages.text(lang, fields.list(tg).isEmpty() ? "fields.empty" : "weather.unavailable"), null);
            return;
        }
        WeatherRisk.Thresholds t = weather.thresholds();
        List<String> blocks = new ArrayList<>();
        blocks.add(messages.text(lang, "weather.title"));
        boolean anyRisk = false;
        for (WeatherService.CellView c : cells) {
            List<String> lines = new ArrayList<>();
            lines.add(messages.text(lang, "weather.fields", names(c.fieldNames())));
            for (DailyForecast d : c.days()) {
                boolean risk = !WeatherRisk.check(d, c.southern(), t).isEmpty();
                anyRisk |= risk;
                String line = (risk ? "⚠️ " : "") + messages.text(lang, "weather.day", weekday(lang, d.date()), icon(d.code()),
                        temp(d.tMin()), temp(d.tMax()), d.gustMs() == null ? "—" : String.valueOf(Math.round(d.gustMs())));
                // .properties qiymat boshidagi bo'shliqni tashlaydi — ajratuvchi kodda
                if (d.rainMm() != null && d.rainMm() >= 1) line += "  " + messages.text(lang, "weather.rain", String.valueOf(Math.round(d.rainMm())));
                lines.add(line);
            }
            blocks.add(String.join("\n", lines));
        }
        if (anyRisk) blocks.add(messages.text(lang, "weather.legend"));
        sender.send(chatId, String.join("\n\n", blocks), null);
    }

    /** Bildirishnoma kanali: {kind, level, date, value, fields, escalated} — @return true: yuborildi */
    public boolean alert(Recipient to, Map<String, Object> p) {
        String lang = to.language();
        String kind = String.valueOf(p.get("kind"));
        LocalDate date = LocalDate.parse(String.valueOf(p.get("date")));
        double value = ((Number) p.get("value")).doubleValue();
        String shown = "FROST".equals(kind) || "HEAT".equals(kind) ? temp(value) : String.valueOf(Math.round(value));
        List<String> lines = new ArrayList<>();
        // Do'l (WMO 96/99) yog'insiz ham keladi — "0 mm gacha" emas, alohida matn
        String key = "RAIN".equals(kind) && value < 1 ? "weather.alert.HAIL" : "weather.alert." + kind + "." + p.get("level");
        lines.add(messages.text(lang, key, weekday(lang, date), shown));
        if (Boolean.TRUE.equals(p.get("escalated"))) lines.add(messages.text(lang, "weather.alert.escalated"));
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) p.get("fields");
        if (names != null && !names.isEmpty()) lines.add(messages.text(lang, "weather.alert.fields", names(names)));
        lines.add("");
        lines.add(messages.text(lang, "weather.advice." + kind));
        lines.add(messages.text(lang, "weather.alert.footer"));
        return sender.send(to.chatId(), String.join("\n", lines), null) != null;
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    /** "Pa 16.10" — hafta kuni fermer tilida (JDK CLDR: uz/ru/en) */
    private static String weekday(String lang, LocalDate d) {
        String w = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag(lang));
        return Character.toUpperCase(w.charAt(0)) + w.substring(1) + ", " + BotMessages.date(lang, d);
    }

    private static String temp(Double v) {
        if (v == null) return "—";
        long r = Math.round(v);
        return r > 0 ? "+" + r : String.valueOf(r);
    }

    private static String names(List<String> all) {
        String shown = String.join(", ", all.subList(0, Math.min(all.size(), MAX_NAMES)));
        return all.size() > MAX_NAMES ? shown + " (+" + (all.size() - MAX_NAMES) + ")" : shown;
    }

    /** WMO weather code → emoji */
    private static String icon(Integer code) {
        if (code == null) return "🌡";
        if (code == 0) return "☀️";
        if (code <= 2) return "🌤";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫";
        if (code >= 95) return "⛈";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄️";
        if (code >= 80) return "🌦";
        return "🌧";                                        // 51–67 — mayda va oddiy yomg'ir
    }
}
