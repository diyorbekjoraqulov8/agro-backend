package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.field.FieldBotService;
import uz.app.agro.field.FieldBotService.FieldCard;
import uz.app.agro.irrigation.FieldWaterEntity;
import uz.app.agro.irrigation.IrrigationEventEntity;
import uz.app.agro.irrigation.IrrigationService;
import uz.app.agro.irrigation.SoilType;
import uz.app.agro.irrigation.WaterStatus;
import uz.app.agro.notification.NotificationChannel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sug'orish eslatmasi (bot tomoni): sozlash savollari, "✅ Sug'ordim", eslatmalarni yuborish.
 * callback_data: wp:<dala> (sozlash), ws:<dala>:<SAND|LOAM|CLAY|AUTO>, wl:<dala>:<kun>, wi:<dala>, wo:<dala>.
 * Hammasi ISHONCHSIZ — IrrigationService egalikni tekshiradi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotIrrigation {

    static final String SETUP = "wp:", SOIL = "ws:", LAST = "wl:", IRRIGATED = "wi:", TOGGLE = "wo:";
    private static final int[] LAST_DAYS = {0, 2, 7, 14};
    private static final int OVERVIEW_MAX = 10;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");

    private final IrrigationService irrigation;
    private final BotMessages m;
    private final BotSender sender;
    private final FieldBotService fields;
    private final BotKeyboards keyboards;

    // ── Menyu "💧 Sug'orish": barcha dalalar holati bir xabarda ────────────

    public void overview(long tg, long chatId, String lang) {
        List<FieldCard> list = fields.list(tg);
        if (list.isEmpty()) {
            sender.send(chatId, m.text(lang, "fields.empty"), null);
            return;
        }
        // Har dala holati — bir necha so'rov (status hisobi); cheksiz tarifda ham xabar 4096 belgidan oshmasin
        List<FieldCard> shown = list.subList(0, Math.min(list.size(), OVERVIEW_MAX));
        List<String> lines = new ArrayList<>();
        lines.add(m.text(lang, "water.overview.title"));
        for (FieldCard f : shown) {
            String status = cardLine(lang, tg, f.id());
            lines.add(status.isEmpty() ? "📍 " + f.name() : m.text(lang, "water.overview.line", f.name(), status));
        }
        if (list.size() > shown.size()) lines.add(m.text(lang, "water.overview.more", String.valueOf(list.size() - shown.size())));
        lines.add(m.text(lang, "water.overview.hint"));
        // Dala tugmasi — kartochka (u yerda "Sug'ordim", eslatmani yoqish/o'chirish, sozlash)
        sender.send(chatId, String.join("\n\n", lines), keyboards.fieldList(shown));
    }

    // ── Sozlash ────────────────────────────────────────────────────────────

    /** Hisobotdan keyin: eslatma hali sozlanmagan bo'lsa — tuproq savoli */
    public void promptIfNeeded(long tg, long chatId, String lang, long fieldId) {
        Optional<WaterStatus> s = irrigation.status(tg, fieldId);
        if (s.isPresent() && !s.get().configured()) askSoil(chatId, lang, fieldId, s.get().fieldName());
    }

    void askSoil(long chatId, String lang, long fieldId, String fieldName) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.addRow(btn(m.text(lang, "soil.SAND"), SOIL + fieldId + ":SAND"), btn(m.text(lang, "soil.LOAM"), SOIL + fieldId + ":LOAM"));
        kb.addRow(btn(m.text(lang, "soil.CLAY"), SOIL + fieldId + ":CLAY"), btn(m.text(lang, "soil.AUTO"), SOIL + fieldId + ":AUTO"));
        sender.send(chatId, m.text(lang, "water.setup.soil", fieldName), kb);
    }

    /** @return true — callback shu klassniki edi */
    public boolean onCallback(String cbId, long tg, long chatId, Integer messageId, String lang, String data) {
        String prefix = data.length() >= 3 ? data.substring(0, 3) : "";
        if (!List.of(SETUP, SOIL, LAST, IRRIGATED, TOGGLE).contains(prefix)) return false;
        String[] parts = data.substring(3).split(":");
        long fieldId;
        try {
            fieldId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            sender.answer(cbId, null);
            return true;
        }
        sender.answer(cbId, null);
        switch (prefix) {
            case SETUP -> irrigation.status(tg, fieldId).ifPresentOrElse(
                    s -> askSoil(chatId, lang, fieldId, s.fieldName()), () -> notFound(chatId, lang));
            case SOIL -> {
                SoilType choice = parts.length > 1 && !"AUTO".equals(parts[1]) ? parse(parts[1]) : null;
                if (parts.length < 2 || (choice == null && !"AUTO".equals(parts[1]))) return true;
                irrigation.setSoil(tg, fieldId, choice).ifPresentOrElse(r -> {
                    if (messageId != null) sender.edit(chatId, messageId, soilAnswered(lang, r), null);
                    InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    for (int d : LAST_DAYS) {
                        row.add(btn(m.text(lang, "last." + d), LAST + fieldId + ":" + d));
                        if (row.size() == 2) { kb.addRow(row.toArray(InlineKeyboardButton[]::new)); row.clear(); }
                    }
                    sender.send(chatId, m.text(lang, "water.setup.last"), kb);
                }, () -> notFound(chatId, lang));
            }
            case LAST -> {
                int days;
                try {
                    days = Integer.parseInt(parts.length > 1 ? parts[1] : "x");
                } catch (NumberFormatException e) {
                    return true;
                }
                if (java.util.Arrays.stream(LAST_DAYS).noneMatch(x -> x == days)) return true;
                var s = safe(() -> irrigation.setLastIrrigation(tg, fieldId, days, IrrigationEventEntity.Source.SETUP));
                if (s == null) { notFound(chatId, lang); return true; }
                if (messageId != null) sender.edit(chatId, messageId, m.text(lang, "water.setup.last") + "\n✅ " + m.text(lang, "last." + days), null);
                sender.send(chatId, m.text(lang, "water.enabled", nextLine(lang, s)) + "\n\n" + m.text(lang, "water.howto"),
                        actions(lang, s));
            }
            case IRRIGATED -> {
                var s = safe(() -> irrigation.setLastIrrigation(tg, fieldId, 0, IrrigationEventEntity.Source.FARMER));
                if (s == null) { notFound(chatId, lang); return true; }
                sender.send(chatId, m.text(lang, "water.irrigatedSaved", s.fieldName(), nextLine(lang, s)), null);
            }
            case TOGGLE -> irrigation.toggle(tg, fieldId).ifPresentOrElse(
                    s -> sender.send(chatId, m.text(lang, s.enabled() ? "water.toggled.on" : "water.toggled.off", s.fieldName()), null),
                    () -> notFound(chatId, lang));
            default -> { }
        }
        return true;
    }

    // ── Eslatma (IrrigationReminderJob → bu yer) ───────────────────────────

    /**
     * Bildirishnoma kanali chaqiradi (Templates.IRRIGATION_REMINDER) — @return true: yuborildi.
     * Chat va til — yuborish paytidagi fermerdan (to), navbatga qo'yilgandagi snapshot'dan emas: orada tilni o'zgartirgan bo'lishi mumkin.
     */
    public boolean remind(NotificationChannel.Recipient to, WaterStatus s, FieldWaterEntity.ReminderKind kind) {
        String lang = to.language();
        List<String> lines = new ArrayList<>();
        switch (kind) {
            case DUE -> lines.add(m.text(lang, "water.remind.DUE", s.fieldName(), pct(s.depletionPct())));
            case SOON -> lines.add(m.text(lang, "water.remind.SOON", s.fieldName(), String.valueOf(s.daysUntil()), day(s.nextIrrigationOn())));
            case STATUS -> lines.add(s.nextIrrigationOn() != null
                    ? m.text(lang, "water.remind.STATUS", s.fieldName(), day(s.nextIrrigationOn()))
                    : m.text(lang, "water.remind.STATUS.later", s.fieldName()));
        }
        // Yomg'ir kutilsa — sug'orishni kechiktirish mumkin (DUE/SOON da foydali)
        if (kind != FieldWaterEntity.ReminderKind.STATUS && s.rainNext3dMm() != null && s.rainNext3dMm() >= 10) {
            lines.add(m.text(lang, "water.rain", String.valueOf(Math.round(s.rainNext3dMm()))));
        }
        if (s.lastIrrigatedOn() != null) lines.add(m.text(lang, "water.lastIrrigated", day(s.lastIrrigatedOn())));
        return sender.send(to.chatId(), String.join("\n", lines), actions(lang, s)) != null;
    }

    // ── Dala kartochkasi uchun ─────────────────────────────────────────────

    public String cardLine(String lang, long tg, long fieldId) {
        return irrigation.status(tg, fieldId).map(s -> {
            if (!s.configured()) return m.text(lang, "field.water.notSet");
            if (!s.enabled()) return m.text(lang, "field.water.off");
            if (s.noCrop()) return m.text(lang, "water.next.noCrop");
            if (s.due()) return m.text(lang, "field.water.due");
            return s.nextIrrigationOn() != null ? m.text(lang, "field.water.next", day(s.nextIrrigationOn()))
                    : m.text(lang, "field.water.later");
        }).orElse("");
    }

    public List<InlineKeyboardButton> cardButtons(String lang, long tg, long fieldId) {
        return irrigation.status(tg, fieldId).map(s -> s.configured()
                ? List.of(btn(m.text(lang, "btn.irrigated"), IRRIGATED + fieldId),
                          btn(m.text(lang, s.enabled() ? "btn.remindersOff" : "btn.remindersOn"), TOGGLE + fieldId))
                : List.of(btn(m.text(lang, "btn.waterSetup"), SETUP + fieldId))).orElse(List.of());
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    private InlineKeyboardMarkup actions(String lang, WaterStatus s) {
        return new InlineKeyboardMarkup(btn(m.text(lang, "btn.irrigated"), IRRIGATED + s.fieldId()),
                btn(m.text(lang, s.enabled() ? "btn.remindersOff" : "btn.remindersOn"), TOGGLE + s.fieldId()));
    }

    private String nextLine(String lang, WaterStatus s) {
        if (s.noCrop()) return m.text(lang, "water.next.noCrop");
        if (s.due()) return m.text(lang, "water.next.due");
        if (s.nextIrrigationOn() != null) return m.text(lang, "water.next.days", day(s.nextIrrigationOn()), String.valueOf(s.daysUntil()));
        return m.text(lang, "water.next.later");
    }

    private String soilAnswered(String lang, IrrigationService.SoilResult r) {
        String q = m.text(lang, "water.setup.soil.short");
        String chosen = r.farmerChoice() == null ? m.text(lang, "soil.AUTO") : m.text(lang, "soilName." + r.farmerChoice());
        String note = switch (r.check()) {
            case MATCH -> m.text(lang, "water.soil.match", m.text(lang, "soilName." + r.auto()));
            case MISMATCH -> m.text(lang, "water.soil.mismatch", m.text(lang, "soilName." + r.farmerChoice()), m.text(lang, "soilName." + r.auto()));
            case AUTO_ONLY -> m.text(lang, "water.soil.auto", m.text(lang, "soilName." + r.auto()));
            case UNAVAILABLE -> r.farmerChoice() == null ? m.text(lang, "water.soil.unknown") : "";
        };
        return q + "\n✅ " + chosen + (note.isEmpty() ? "" : "\n\n" + note);
    }

    private void notFound(long chatId, String lang) {
        sender.send(chatId, m.text(lang, "field.notFound"), null);
    }

    // Python/Open-Meteo ishlamasa ham fermerga javob qaytsin
    private static WaterStatus safe(java.util.function.Supplier<Optional<WaterStatus>> f) {
        try {
            return f.get().orElse(null);
        } catch (RuntimeException e) {
            log.warn("sug'orish hisobi bajarilmadi: {}", e.getMessage());
            return null;
        }
    }

    private static SoilType parse(String s) {
        try {
            return SoilType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static InlineKeyboardButton btn(String text, String data) {
        return new InlineKeyboardButton(text).callbackData(data);
    }

    private static String day(LocalDate d) {
        return d == null ? "—" : d.format(DAY);
    }

    private static String pct(Double v) {
        return v == null ? "—" : String.valueOf(Math.min(100, Math.round(v)));
    }
}
