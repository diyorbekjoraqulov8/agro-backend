package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.common.HumanDates;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.field.FieldSeasonEntity.Sowing;
import uz.app.agro.field.FieldSeasonService;
import uz.app.agro.field.FieldSeasonService.SeasonView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ekin mavsumi (H1): dala qo'shilgach "Qachon ekdingiz?" → "Qanday sug'orasiz?"; dala kartasida qator va tugmalar.
 * callback_data (ISHONCHSIZ — FieldSeasonService egalik va sanani tekshiradi):
 * sw:<dala> — savol; sw:<dala>:<W|M|B|N|U> — tez javob; sw:<dala>:P — oy tanlash; sw:<dala>:P<yyyyMM> — kun;
 * sw:<dala>:D<yyyyMMdd> — aniq sana; si:<dala>:<IrrigationType>; sh:<dala> — hosil (tasdiq); sy:<dala> — ha.
 * Sana kiritish — tugmalar bilan (matn rejimi yo'q: "12.10" / "12 okt" tahlili va holat kerak bo'lmaydi).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotSeason {

    static final String SOW = "sw:", IRRIGATION = "si:", HARVEST = "sh:", HARVEST_YES = "sy:";
    /** Oy tanlashda — joriy oy va undan oldingi 11 ta (FieldSeasonService.MAX_AGE_DAYS ichida) */
    private static final int PICK_MONTHS = 12;
    // STRICT: soxta "20260231" 28-fevralga "tuzatilmasin" — rad
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuuMM").withResolverStyle(ResolverStyle.STRICT),
            DAY = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    private final FieldSeasonService seasons;
    private final BotMessages m;
    private final BotSender sender;

    /** Yangi dala (yoki ekin almashtirildi) — yangi xabar bilan so'raladi */
    public void ask(long chatId, String lang, long fieldId, String fieldName) {
        sender.send(chatId, m.text(lang, "season.ask", fieldName), sowingButtons(lang, fieldId));
    }

    /** Dala kartasi uchun: qatorlar (ekish sanasi — necha kun bo'ldi, sug'orish usuli) va tugmalar; dala yo'q — bo'sh */
    public record Card(String line, List<InlineKeyboardButton> buttons) {}

    public Card card(String lang, long tg, long fieldId) {
        return seasons.view(tg, fieldId).map(v -> {
            String line = sowingLine(lang, v);
            if (v.irrigation() != null) line += "\n" + m.text(lang, "season.line.irrigation", m.label(lang, v.irrigation()));
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(btn(m.text(lang, "btn.sowing"), SOW + fieldId));
            if (v.sowing() == Sowing.SOWN) row.add(btn(m.text(lang, "btn.harvest"), HARVEST + fieldId));
            return new Card(line, row);
        }).orElse(new Card("", List.of()));
    }

    public boolean onCallback(String cbId, long tg, long chatId, Integer messageId, String lang, String data) {
        String prefix = data.length() >= 3 ? data.substring(0, 3) : "";
        if (!List.of(SOW, IRRIGATION, HARVEST, HARVEST_YES).contains(prefix)) return false;
        sender.answer(cbId, null);
        String[] parts = data.substring(3).split(":");
        long fieldId;
        try {
            fieldId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return true;
        }
        String arg = parts.length > 1 ? parts[1] : "";
        switch (prefix) {
            case SOW -> onSowing(tg, chatId, messageId, lang, fieldId, arg);
            case IRRIGATION -> {
                IrrigationType type = parse(arg);
                if (type == null) return true;
                seasons.setIrrigation(tg, fieldId, type).ifPresentOrElse(v -> show(chatId, messageId,
                        m.text(lang, "season.saved", v.fieldName()) + "\n" + sowingLine(lang, v) + "\n"
                                + m.text(lang, "season.line.irrigation", m.label(lang, type)),
                        new InlineKeyboardMarkup(btn(m.text(lang, "btn.toField"), BotKeyboards.FIELD_VIEW + fieldId))),
                        () -> show(chatId, messageId, m.text(lang, "field.notFound"), null));
            }
            case HARVEST -> seasons.view(tg, fieldId).ifPresentOrElse(v -> show(chatId, messageId,
                    m.text(lang, "season.harvestConfirm", v.fieldName()), new InlineKeyboardMarkup(
                            btn(m.text(lang, "season.btn.harvestYes"), HARVEST_YES + fieldId),
                            btn(m.text(lang, "btn.no"), BotKeyboards.FIELD_VIEW + fieldId))),
                    () -> show(chatId, messageId, m.text(lang, "field.notFound"), null));
            // Bo'sh — ekilgan mavsum yo'q (ikkinchi bosish yoki soxta callback; begona dala ham — farqi oshkor qilinmaydi)
            case HARVEST_YES -> seasons.harvest(tg, fieldId).ifPresentOrElse(v -> show(chatId, messageId,
                    m.text(lang, "season.harvested", v.fieldName()), new InlineKeyboardMarkup(
                            btn(m.text(lang, "btn.sowing"), SOW + fieldId),
                            btn(m.text(lang, "btn.changeCrop"), BotKeyboards.FIELD_CROP + fieldId))),
                    () -> show(chatId, messageId, m.text(lang, "season.harvestNone"),
                            new InlineKeyboardMarkup(btn(m.text(lang, "btn.toField"), BotKeyboards.FIELD_VIEW + fieldId))));
            default -> { }
        }
        return true;
    }

    private void onSowing(long tg, long chatId, Integer messageId, String lang, long fieldId, String arg) {
        Optional<SeasonView> field = seasons.view(tg, fieldId);
        if (field.isEmpty()) {
            show(chatId, messageId, m.text(lang, "field.notFound"), null);
            return;
        }
        LocalDate today = LocalDate.now();
        if (arg.isEmpty()) {
            show(chatId, messageId, m.text(lang, "season.ask", field.get().fieldName()), sowingButtons(lang, fieldId));
            return;
        }
        if (arg.equals("P")) {
            show(chatId, messageId, m.text(lang, "season.pickMonth"), monthButtons(lang, fieldId, today));
            return;
        }
        if (arg.startsWith("P")) {
            YearMonth month = parseMonth(arg.substring(1));
            YearMonth current = YearMonth.from(today);
            if (month == null || month.isAfter(current) || month.isBefore(current.minusMonths(PICK_MONTHS - 1))) return;
            show(chatId, messageId, m.text(lang, "season.pickDay", HumanDates.month(lang, month)), dayButtons(fieldId, month, today, lang));
            return;
        }
        // Tez javob — aynan bitta harf ("W2" kabi soxta qiymat — rad)
        char kind = arg.length() == 1 || arg.charAt(0) == 'D' ? arg.charAt(0) : '?';
        Optional<SeasonView> saved = switch (kind) {
            case 'W' -> seasons.setSowing(tg, fieldId, Sowing.SOWN, today.minusDays(3), true);
            case 'M' -> seasons.setSowing(tg, fieldId, Sowing.SOWN, today.minusDays(21), true);
            case 'B' -> seasons.setSowing(tg, fieldId, Sowing.SOWN, today.minusDays(45), true);
            case 'N' -> seasons.setSowing(tg, fieldId, Sowing.NOT_SOWN, null, false);
            case 'U' -> seasons.setSowing(tg, fieldId, Sowing.UNKNOWN, null, false);
            case 'D' -> {
                LocalDate d = parseDay(arg.substring(1));
                yield d == null ? Optional.empty() : seasons.setSowing(tg, fieldId, Sowing.SOWN, d, false);
            }
            default -> Optional.empty();
        };
        // Bo'sh — eskirgan (oy tanlagich uzoq ochiq qolgan) yoki soxta sana: savol boshidan
        saved.ifPresentOrElse(v -> show(chatId, messageId, sowingLine(lang, v) + "\n\n" + m.text(lang, "season.irrigation.ask", v.fieldName()),
                irrigationButtons(lang, fieldId, v.irrigation())),
                () -> show(chatId, messageId, m.text(lang, "season.ask", field.get().fieldName()), sowingButtons(lang, fieldId)));
    }

    private String sowingLine(String lang, SeasonView v) {
        return switch (v.sowing()) {
            case SOWN -> m.text(lang, v.approx() ? "season.line.sownApprox" : "season.line.sown",
                    BotMessages.date(lang, v.sownOn()), ChronoUnit.DAYS.between(v.sownOn(), LocalDate.now()));
            case NOT_SOWN -> m.text(lang, "season.line.notSown");
            case UNANSWERED, UNKNOWN -> m.text(lang, "season.line.unknown");
        };
    }

    private InlineKeyboardMarkup sowingButtons(String lang, long fieldId) {
        String p = SOW + fieldId + ":";
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.addRow(btn(m.text(lang, "season.btn.week"), p + "W"), btn(m.text(lang, "season.btn.weeks"), p + "M"));
        kb.addRow(btn(m.text(lang, "season.btn.months"), p + "B"), btn(m.text(lang, "season.btn.pick"), p + "P"));
        kb.addRow(btn(m.text(lang, "season.btn.notYet"), p + "N"), btn(m.text(lang, "season.btn.unknown"), p + "U"));
        return kb;
    }

    private InlineKeyboardMarkup monthButtons(String lang, long fieldId, LocalDate today) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        YearMonth month = YearMonth.from(today);
        for (int i = 0; i < PICK_MONTHS; i++, month = month.minusMonths(1)) {
            row.add(btn(HumanDates.month(lang, month), SOW + fieldId + ":P" + month.format(MONTH)));
            if (row.size() == 3) { kb.addRow(row.toArray(InlineKeyboardButton[]::new)); row.clear(); }
        }
        if (!row.isEmpty()) kb.addRow(row.toArray(InlineKeyboardButton[]::new));
        kb.addRow(btn(m.text(lang, "season.btn.backAsk"), SOW + fieldId));
        return kb;
    }

    /** Oy kunlari 7 tadan; joriy oyda — bugungacha (kelajak sana yo'q) */
    private InlineKeyboardMarkup dayButtons(long fieldId, YearMonth month, LocalDate today, String lang) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        int last = month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : month.lengthOfMonth();
        for (int d = 1; d <= last; d++) {
            row.add(btn(String.valueOf(d), SOW + fieldId + ":D" + month.atDay(d).format(DAY)));
            if (row.size() == 7) { kb.addRow(row.toArray(InlineKeyboardButton[]::new)); row.clear(); }
        }
        if (!row.isEmpty()) kb.addRow(row.toArray(InlineKeyboardButton[]::new));
        kb.addRow(btn(m.text(lang, "season.btn.backMonths"), SOW + fieldId + ":P"));
        return kb;
    }

    /** Joriy javob ✓ bilan belgilanadi (dala qo'shilganda — fermerning onboarding javobi) */
    private InlineKeyboardMarkup irrigationButtons(String lang, long fieldId, IrrigationType current) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (IrrigationType t : IrrigationType.values()) {
            row.add(btn((t == current ? "✓ " : "") + m.label(lang, t), IRRIGATION + fieldId + ":" + t.name()));
            if (row.size() == 2) { kb.addRow(row.toArray(InlineKeyboardButton[]::new)); row.clear(); }
        }
        if (!row.isEmpty()) kb.addRow(row.toArray(InlineKeyboardButton[]::new));
        return kb;
    }

    /** Kartadan bosilgan — o'sha xabar almashadi; messageId yo'q (eski xabar) — yangisi */
    private void show(long chatId, Integer messageId, String text, InlineKeyboardMarkup kb) {
        if (messageId != null) sender.edit(chatId, messageId, text, kb);
        else sender.send(chatId, text, kb);
    }

    private static YearMonth parseMonth(String s) {
        try {
            return YearMonth.parse(s, MONTH);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate parseDay(String s) {
        try {
            return LocalDate.parse(s, DAY);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static IrrigationType parse(String s) {
        try {
            return IrrigationType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static InlineKeyboardButton btn(String text, String data) {
        return new InlineKeyboardButton(text).callbackData(data);
    }
}
