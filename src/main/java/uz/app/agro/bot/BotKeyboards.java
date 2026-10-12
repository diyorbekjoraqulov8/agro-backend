package uz.app.agro.bot;

import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.farmer.WaterSource;
import uz.app.agro.feedback.FeedbackKind;
import uz.app.agro.field.Crop;

import uz.app.agro.field.FieldBotService.FieldCard;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * callback_data — 64 bayt chegarasi, qisqa prefiks: c: (ekin), i: (sug'orish), w: (suv), l: (til).
 * Mijoz callback_data'ni o'zgartira oladi — BotUpdateHandler uni ishonchsiz kirish deb tekshiradi.
 */
@Component
@RequiredArgsConstructor
public class BotKeyboards {

    static final String CROP = "c:", IRRIGATION = "i:", WATER = "w:", LANG = "l:";
    // Dalalar va hisobot: f* — dala id, r* — tahlil (job) id. Egalik service'da tekshiriladi
    static final String FIELDS = "fl", FIELD_VIEW = "fv:", FIELD_REPORT = "fp:", FIELD_REANALYZE = "fr:",
            FIELD_EDIT = "fe:", FIELD_DELETE = "fd:", FIELD_DELETE_YES = "fy:", REPORT_PDF = "rp:", REPORT_CSV = "rc:",
            FIELD_CROP = "fc:", FIELD_CROP_SET = "fk:";
    // Murojaat: tur "fb:PROBLEM"; suhbat — fw:<id> (javob yozish), fx:<id> (yakunlash), fs:<id>:<1-5> (baho)
    static final String FEEDBACK = "fb:", FEEDBACK_WRITE = "fw:", FEEDBACK_END = "fx:", FEEDBACK_RATE = "fs:";
    // Onboarding javobi: "ob:CROP:COTTON"
    static final String ONBOARDING = "ob:";

    private final BotMessages messages;
    private final TelegramProperties props;

    public boolean miniAppConfigured() {
        return props.miniappUrl() != null && !props.miniappUrl().isEmpty();
    }

    /**
     * Asosiy menyu — DOIMIY reply keyboard: "🗺 Yangi dala" (Mini App) va "📋 Dalalarim".
     * Xizmat bir martalik emas — tahlildan keyin ham yangi dala qo'shish/boshqarish mumkin.
     */
    public ReplyKeyboardMarkup mainMenu(String lang, BigDecimal maxHa, Crop defaultCrop) {
        KeyboardButton newField = new KeyboardButton(messages.text(lang, "menu.newField"));
        // crop — Mini App'dagi ekin tanlovining boshlang'ich qiymati (fermerning asosiy ekini)
        if (miniAppConfigured()) newField.webAppInfo(new WebAppInfo(miniAppUrl(lang, maxHa,
                defaultCrop != null ? "&crop=" + defaultCrop.name() : null)));
        // Bo'limlar: dalalar · do'stlar va yordam · sozlamalar (til, tarif). Keyingi fazalarda ob-havo, AI, daftar qo'shiladi
        return new ReplyKeyboardMarkup(
                new KeyboardButton[]{newField, new KeyboardButton(messages.text(lang, "menu.myFields"))},
                new KeyboardButton[]{new KeyboardButton(messages.text(lang, "menu.friends")),
                        new KeyboardButton(messages.text(lang, "menu.feedback"))},
                new KeyboardButton[]{new KeyboardButton(messages.text(lang, "menu.settings"))})
                .resizeKeyboard(true)
                .isPersistent(true);
    }

    /** Onboarding: inline "🗺 Dalani xaritada belgilash" (Mini App → HTTP, initData imzosi). Sozlanmagan — null */
    public InlineKeyboardMarkup mapInline(String lang, BigDecimal maxHa, Crop defaultCrop) {
        if (!props.inlineMapEnabled()) return null;
        String extra = (defaultCrop != null ? "&crop=" + defaultCrop.name() : "")
                + "&api=" + URLEncoder.encode(props.publicApiUrl(), StandardCharsets.UTF_8);
        return new InlineKeyboardMarkup(new InlineKeyboardButton(messages.text(lang, "onboarding.mapButton"))
                .webApp(new WebAppInfo(miniAppUrl(lang, maxHa, extra))));
    }

    /**
     * Onboarding savoli variantlari: "ob:<KOD>:<QIYMAT>" (≤ 64 bayt: kod ≤ 32, qiymat ≤ 24).
     * labels — tugma matnlari (o'rnatilgan savolda enum yorlig'i, maxsusda admin matni), tartib — values bilan bir xil.
     */
    public InlineKeyboardMarkup onboardingOptions(String code, java.util.List<String> values, java.util.List<String> labels) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        java.util.List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            row.add(new InlineKeyboardButton(labels.get(i)).callbackData(ONBOARDING + code + ":" + values.get(i)));
            if (row.size() == 2) {
                kb.addRow(row.toArray(InlineKeyboardButton[]::new));
                row.clear();
            }
        }
        if (!row.isEmpty()) kb.addRow(row.toArray(InlineKeyboardButton[]::new));
        return kb;
    }

    /** Murojaat matnini kutayotganda — faqat "Bekor qilish" (menyu tugmasi tasodifan bosilmasin) */
    public ReplyKeyboardMarkup cancelOnly(String lang) {
        return new ReplyKeyboardMarkup(new KeyboardButton[]{new KeyboardButton(messages.text(lang, "menu.cancel"))})
                .resizeKeyboard(true)
                .isPersistent(true);
    }

    /** Suhbat rejimi: yakunlash yoki asosiy menyuga (suhbat ochiq qoladi) */
    public ReplyKeyboardMarkup chatMenu(String lang) {
        return new ReplyKeyboardMarkup(new KeyboardButton[]{
                new KeyboardButton(messages.text(lang, "menu.endChat")), new KeyboardButton(messages.text(lang, "menu.home"))})
                .resizeKeyboard(true)
                .isPersistent(true);
    }

    /** Operator xabari ostida */
    public InlineKeyboardMarkup staffMessage(String lang, long feedbackId) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton(messages.text(lang, "btn.fb.write")).callbackData(FEEDBACK_WRITE + feedbackId),
                new InlineKeyboardButton(messages.text(lang, "btn.fb.end")).callbackData(FEEDBACK_END + feedbackId));
    }

    public InlineKeyboardMarkup rating(long feedbackId) {
        InlineKeyboardButton[] row = new InlineKeyboardButton[5];
        for (int i = 1; i <= 5; i++) {
            row[i - 1] = new InlineKeyboardButton(i + " ⭐").callbackData(FEEDBACK_RATE + feedbackId + ":" + i);
        }
        return new InlineKeyboardMarkup(row);
    }

    public InlineKeyboardMarkup feedbackKinds(String lang) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        for (FeedbackKind k : FeedbackKind.values()) {
            kb.addRow(new InlineKeyboardButton(messages.text(lang, "btn.fb." + k.name())).callbackData(FEEDBACK + k.name()));
        }
        return kb;
    }

    /** Tahrirlash: Mini App eski chegara bilan ochiladi (sendData uchun yana REPLY tugma). */
    public ReplyKeyboardMarkup editMap(String lang, BigDecimal maxHa, long fieldId, String name, Crop crop, List<List<Number>> ring) {
        String extra = "&fieldId=" + fieldId + "&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&crop=" + crop.name()
                + "&poly=" + URLEncoder.encode(Polyline.encode(ring), StandardCharsets.UTF_8);
        return new ReplyKeyboardMarkup(
                new KeyboardButton[]{new KeyboardButton(messages.text(lang, "field.editButton", name))
                        .webAppInfo(new WebAppInfo(miniAppUrl(lang, maxHa, extra)))},
                new KeyboardButton[]{new KeyboardButton(messages.text(lang, "menu.cancel"))})
                .resizeKeyboard(true)
                .isPersistent(true);
    }

    private String miniAppUrl(String lang, BigDecimal maxHa, String extra) {
        String base = props.miniappUrl();
        return base + (base.contains("?") ? "&" : "?") + "lang=" + lang
                + (maxHa != null ? "&maxHa=" + maxHa.stripTrailingZeros().toPlainString() : "")
                + (extra != null ? extra : "");
    }

    public InlineKeyboardMarkup fieldList(List<FieldCard> fields) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        for (FieldCard f : fields) {
            kb.addRow(new InlineKeyboardButton("📍 " + f.name()).callbackData(FIELD_VIEW + f.id()));
        }
        return kb;
    }

    public InlineKeyboardMarkup fieldActions(String lang, FieldCard f) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        if (f.lastDoneJobId() != null) {
            kb.addRow(new InlineKeyboardButton(messages.text(lang, "btn.report")).callbackData(FIELD_REPORT + f.id()),
                    new InlineKeyboardButton(messages.text(lang, "btn.reanalyze")).callbackData(FIELD_REANALYZE + f.id()));
        } else {
            kb.addRow(new InlineKeyboardButton(messages.text(lang, "btn.reanalyze")).callbackData(FIELD_REANALYZE + f.id()));
        }
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "btn.edit")).callbackData(FIELD_EDIT + f.id()),
                new InlineKeyboardButton(messages.text(lang, "btn.changeCrop")).callbackData(FIELD_CROP + f.id()));
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "btn.delete")).callbackData(FIELD_DELETE + f.id()));
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "btn.back")).callbackData(FIELDS));
        return kb;
    }

    /** Almashlab ekish: "fk:<dala>:<CROP>" */
    public InlineKeyboardMarkup fieldCrops(String lang, long fieldId) {
        InlineKeyboardMarkup kb = grid(lang, FIELD_CROP_SET + fieldId + ":", Crop.values());
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "btn.back")).callbackData(FIELD_VIEW + fieldId));
        return kb;
    }

    public InlineKeyboardMarkup confirmDelete(String lang, long fieldId) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton(messages.text(lang, "btn.yesDelete")).callbackData(FIELD_DELETE_YES + fieldId),
                new InlineKeyboardButton(messages.text(lang, "btn.no")).callbackData(FIELD_VIEW + fieldId));
    }

    public InlineKeyboardMarkup reportFiles(String lang, long jobId) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton(messages.text(lang, "btn.pdf")).callbackData(REPORT_PDF + jobId),
                new InlineKeyboardButton(messages.text(lang, "btn.csv")).callbackData(REPORT_CSV + jobId));
    }


    public InlineKeyboardMarkup crops(String lang) {
        return grid(lang, CROP, Crop.values());
    }

    public InlineKeyboardMarkup irrigation(String lang) {
        return grid(lang, IRRIGATION, IrrigationType.values());
    }

    public InlineKeyboardMarkup waterSources(String lang) {
        return grid(lang, WATER, WaterSource.values());
    }

    public InlineKeyboardMarkup languages() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton("🇺🇿 Oʻzbekcha").callbackData(LANG + "uz"),
                new InlineKeyboardButton("🇷🇺 Русский").callbackData(LANG + "ru"),
                new InlineKeyboardButton("🇬🇧 English").callbackData(LANG + "en"));
    }

    // 2 ustun — telefonda tugma matni sig'adi
    private InlineKeyboardMarkup grid(String lang, String prefix, Enum<?>[] values) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (Enum<?> v : values) {
            row.add(new InlineKeyboardButton(messages.label(lang, v)).callbackData(prefix + v.name()));
            if (row.size() == 2) {
                kb.addRow(row.toArray(InlineKeyboardButton[]::new));
                row.clear();
            }
        }
        if (!row.isEmpty()) kb.addRow(row.toArray(InlineKeyboardButton[]::new));
        return kb;
    }
}
