package uz.app.agro.bot;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.field.Crop;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * "🏢 Agro Biznes" arizasi — xodimga yozishdan oldin 4 ta qisqa savol: maydon → ekinlar (bir nechta) → hudud → telefon.
 * Javoblar bot_states.data.biz da (BUSINESS_FORM — vaqtinchalik rejim, chiqqanda oldingi bosqich qaytadi).
 * Tugagach xodimlar uchun matn qaytariladi — murojaatni BotUpdateHandler ochadi (tarif so'rovi bilan bir xil yo'l).
 * callback_data: bz:start, bz:c:<CROP> (belgilash/olib tashlash), bz:done. Hammasi ISHONCHSIZ — rejim tekshiriladi.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotBusiness {

    static final String PREFIX = "bz:", START = PREFIX + "start", CROP = PREFIX + "c:", DONE = PREFIX + "done";

    private static final String STAGE = "stage", AREA = "area", CROPS = "crops", REGION = "region";
    private static final String S_AREA = "AREA", S_CROPS = "CROPS", S_REGION = "REGION", S_PHONE = "PHONE";
    private static final BigDecimal MAX_AREA = new BigDecimal("1000000");
    private static final int REGION_MAX = 100;
    // "+998 90 123-45-67", "901234567" — raqamlar 9–15 ta
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()\\-]{9,20}$");

    private final BotStateService states;
    private final BotSender sender;
    private final BotMessages messages;
    private final BotKeyboards keyboards;

    public void start(long tg, long chatId, String lang) {
        Map<String, Object> form = new HashMap<>();
        form.put(STAGE, S_AREA);
        states.enterBusiness(tg, form);
        sender.send(chatId, messages.text(lang, "biz.intro") + "\n\n" + messages.text(lang, "biz.ask.area"), keyboards.cancelOnly(lang));
    }

    /**
     * Rejimdagi xabar (bekor qilish va menyu tugmalarini handler o'zi ushlaydi).
     * @return ariza tugadi — xodimlar uchun matn (rejimdan chiqarilgan); aks holda bo'sh
     */
    public Optional<String> onMessage(long tg, long chatId, String lang, Message m) {
        Map<String, Object> form = states.businessForm(tg).orElse(null);
        if (form == null) return Optional.empty();
        String text = m.text() == null ? "" : m.text().strip();
        switch (String.valueOf(form.get(STAGE))) {
            case S_AREA -> {
                BigDecimal area = parseArea(text);
                if (area == null) {
                    sender.send(chatId, messages.text(lang, "biz.ask.area.invalid"), null);
                    return Optional.empty();
                }
                form.put(AREA, area.toPlainString());
                form.put(STAGE, S_CROPS);
                form.put(CROPS, new ArrayList<String>());
                states.saveBusinessForm(tg, form);
                sender.send(chatId, messages.text(lang, "biz.ask.crops"), crops(lang, Set.of()));
            }
            case S_CROPS -> sender.send(chatId, messages.text(lang, "biz.ask.crops"), crops(lang, selected(form)));
            case S_REGION -> {
                if (text.length() < 2 || text.length() > REGION_MAX) {
                    sender.send(chatId, messages.text(lang, "biz.ask.region"), null);
                    return Optional.empty();
                }
                form.put(REGION, text);
                form.put(STAGE, S_PHONE);
                states.saveBusinessForm(tg, form);
                sender.send(chatId, messages.text(lang, "biz.ask.phone"), phoneKeyboard(lang));
            }
            case S_PHONE -> {
                String phone = phone(m, tg, text);
                if (phone == null) {
                    sender.send(chatId, messages.text(lang, "biz.ask.phone.invalid"), phoneKeyboard(lang));
                    return Optional.empty();
                }
                states.leaveFeedback(tg);
                return Optional.of(staffText(form, phone));
            }
            default -> states.leaveFeedback(tg);         // buzuq holat — rejimdan chiqadi
        }
        return Optional.empty();
    }

    /** Rejimda kutilmagan narsa (masalan /start'dan keyingi eslatma) — joriy savolni qayta beradi */
    public void reask(long tg, long chatId, String lang) {
        Map<String, Object> form = states.businessForm(tg).orElse(null);
        String stage = form == null ? S_AREA : String.valueOf(form.get(STAGE));
        switch (stage) {
            case S_CROPS -> sender.send(chatId, messages.text(lang, "biz.ask.crops"), crops(lang, selected(form)));
            case S_REGION -> sender.send(chatId, messages.text(lang, "biz.ask.region"), keyboards.cancelOnly(lang));
            case S_PHONE -> sender.send(chatId, messages.text(lang, "biz.ask.phone"), phoneKeyboard(lang));
            default -> sender.send(chatId, messages.text(lang, "biz.ask.area"), keyboards.cancelOnly(lang));
        }
    }

    /** @return true — bu bo'lim tugmasi edi (bz:start handler'da — ochishdan oldin ro'yxatdan o'tganini tekshiradi) */
    public boolean onCallback(String cbId, long tg, long chatId, Integer messageId, String lang, String data) {
        if (!data.startsWith(PREFIX) || data.equals(START)) return false;
        sender.answer(cbId, null);
        Map<String, Object> form = states.businessForm(tg).orElse(null);
        if (form == null || !S_CROPS.equals(form.get(STAGE))) return true;     // eski xabar tugmasi
        Set<String> chosen = new LinkedHashSet<>(selected(form));
        if (data.startsWith(CROP)) {
            String code = data.substring(CROP.length());
            if (!isCrop(code)) return true;
            if (!chosen.remove(code)) chosen.add(code);
            form.put(CROPS, new ArrayList<>(chosen));
            states.saveBusinessForm(tg, form);
            if (messageId != null) sender.editMarkup(chatId, messageId, crops(lang, chosen));
        } else if (data.equals(DONE)) {
            if (chosen.isEmpty()) {
                sender.send(chatId, messages.text(lang, "biz.ask.crops.empty"), null);
                return true;
            }
            form.put(STAGE, S_REGION);
            states.saveBusinessForm(tg, form);
            if (messageId != null) sender.edit(chatId, messageId, messages.text(lang, "biz.ask.crops") + "\n✅ "
                    + cropNames(lang, chosen), null);
            sender.send(chatId, messages.text(lang, "biz.ask.region"), keyboards.cancelOnly(lang));
        }
        return true;
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    private InlineKeyboardMarkup crops(String lang, Set<String> chosen) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Crop c : Crop.values()) {
            String label = (chosen.contains(c.name()) ? "✅ " : "") + messages.label(lang, c);
            row.add(new InlineKeyboardButton(label).callbackData(CROP + c.name()));
            if (row.size() == 2) {
                kb.addRow(row.toArray(InlineKeyboardButton[]::new));
                row.clear();
            }
        }
        if (!row.isEmpty()) kb.addRow(row.toArray(InlineKeyboardButton[]::new));
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "biz.crops.done")).callbackData(DONE));
        return kb;
    }

    private ReplyKeyboardMarkup phoneKeyboard(String lang) {
        return new ReplyKeyboardMarkup(
                new KeyboardButton[]{new KeyboardButton(messages.text(lang, "biz.phone.button")).requestContact(true)},
                new KeyboardButton[]{new KeyboardButton(messages.text(lang, "menu.cancel"))})
                .resizeKeyboard(true)
                .oneTimeKeyboard(true);
    }

    /** "850", "850 ga", "1 200", "12,5" → gektar; noto'g'ri yoki 1 ga dan kam — null */
    static BigDecimal parseArea(String text) {
        String digits = text.replaceAll("(?i)\\s*(ga|gektar|га|гектар\\p{L}*|ha|hectares?)\\.?$", "")
                .replaceAll("[\\s\u00a0]", "").replace(',', '.');
        if (!digits.matches("\\d{1,7}(\\.\\d{1,2})?")) return null;
        BigDecimal v = new BigDecimal(digits);
        return v.compareTo(BigDecimal.ONE) < 0 || v.compareTo(MAX_AREA) > 0 ? null : v.stripTrailingZeros();
    }

    /** "📱 Raqamni yuborish" — faqat O'ZINING kontakti (boshqa odamniki emas); yoki qo'lda yozilgan raqam */
    private static String phone(Message m, long tg, String text) {
        if (m.contact() != null) {
            Long owner = m.contact().userId();
            return owner != null && owner == tg ? m.contact().phoneNumber() : null;
        }
        if (!PHONE.matcher(text).matches()) return null;
        long digits = text.chars().filter(Character::isDigit).count();
        return digits >= 9 && digits <= 15 ? text : null;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> selected(Map<String, Object> form) {
        Object v = form.get(CROPS);
        return v instanceof List<?> l ? new LinkedHashSet<>((List<String>) l) : Set.of();
    }

    private static boolean isCrop(String code) {
        for (Crop c : Crop.values()) if (c.name().equals(code)) return true;
        return false;
    }

    private String cropNames(String lang, Set<String> codes) {
        List<String> names = new ArrayList<>();
        for (String code : codes) if (isCrop(code)) names.add(messages.label(lang, Crop.valueOf(code)));
        return String.join(", ", names);
    }

    /** Xodimlar o'qiydi — o'zbekcha */
    private String staffText(Map<String, Object> form, String phone) {
        return messages.text("uz", "biz.staff", String.valueOf(form.get(AREA)), cropNames("uz", selected(form)),
                String.valueOf(form.get(REGION)), phone);
    }
}
