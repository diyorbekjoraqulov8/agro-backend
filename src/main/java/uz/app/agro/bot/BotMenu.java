package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.advisor.AdvisorProperties;
import uz.app.agro.billing.PlanService;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.farmer.FarmerSnapshot;
import uz.app.agro.field.FieldBotService;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fermerga bog'liq klaviaturalar: asosiy menyu va Mini App tugmalari (tarif limiti — maxHa, asosiy ekin URL'da).
 * Handler, onboarding va gateway'lar shu yerdan oladi — bir-biriga bog'lanmasin.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotMenu {

    private final BotKeyboards keyboards;
    private final FarmerRegistrationService farmers;
    private final PlanService plans;
    private final SettingsService settings;
    private final FieldBotService fields;
    private final AdvisorProperties advisorProps;
    private final BotSender sender;
    private final BotMessages messages;

    private static final List<Setting> FLAGS = Arrays.stream(BotSection.values())
            .map(s -> s.flag).filter(Objects::nonNull).toList();

    // Shaxsiy chatda chat_id == telegram_user_id (bot faqat shaxsiy chatda ishlaydi)
    public ReplyKeyboardMarkup keyboard(long chatId, String lang) {
        Optional<FarmerSnapshot> farmer = farmers.find(chatId);
        List<BotSection> sections = enabledSections(chatId);
        // Dalalar chegarasi faqat Mini App tugmasi bo'lsa kerak (menyu deyarli har javobga biriktiriladi)
        var rings = sections.contains(BotSection.NEW_FIELD) && keyboards.miniAppConfigured()
                ? fields.rings(chatId, null) : List.<List<List<Number>>>of();
        return keyboards.mainMenu(chatId, lang, farmer.map(this::maxHa).orElse(null),
                farmer.map(FarmerSnapshot::mainCrop).orElse(null), sections, rings);
    }

    /** Admin yoqib qo'ygan bo'limlar (menyu tartibida) — sozlamalar bitta SELECT */
    List<BotSection> enabledSections(long tg) {
        Set<Setting> on = settings.enabled(FLAGS);
        return Arrays.stream(BotSection.values()).filter(s -> available(s, tg, on.contains(s.flag))).toList();
    }

    boolean enabled(BotSection section, long tg) {
        return available(section, tg, section.flag != null && settings.getBool(section.flag));
    }

    /**
     * Sozlamadan tashqari: dalasi yo'q fermerga "🛰 Dalani tekshirish" doim (aks holda dala qo'shishning yo'li qolmaydi —
     * onboarding/qayta /start matni shu tugmaga yuboradi); AI maslahat — kalit bo'lmasa ko'rinmaydi.
     */
    private boolean available(BotSection s, long tg, boolean flagOn) {
        if (s.flag == null) return true;
        if (s == BotSection.NEW_FIELD && !flagOn) return !fields.hasFields(tg);
        if (s == BotSection.ADVISOR) return flagOn && advisorProps.configured();
        return flagOn;
    }

    /**
     * Chat yonidagi "🛰 Tahlil qilish" tugmasi (fermer tilida). /start, onboarding oxiri va til o'zgarganda o'rnatiladi.
     * PUBLIC_API_URL (https) bo'lmasa — o'rnatilmaydi (Mini App dalani yubora olmaydi). Xato — bot baribir ishlaydi
     */
    public void syncChatButton(long chatId, String lang) {
        String url = keyboards.menuButtonUrl(lang);
        if (url == null) return;
        sender.setMenuButton(chatId, new com.pengrad.telegrambot.model.MenuButtonWebApp(
                messages.text(lang, "menu.analyzeButton"), new com.pengrad.telegrambot.model.WebAppInfo(url)));
    }

    /** Mini App (chat tugmasidan) so'raydi: tarif limiti, asosiy ekin, boshqa dalalar (polyline) */
    public record MiniAppContext(BigDecimal maxHa, String crop, List<String> others) {}

    public boolean isFarmer(long telegramUserId) {
        return farmers.find(telegramUserId).isPresent();
    }

    public MiniAppContext miniAppContext(long telegramUserId) {
        Optional<FarmerSnapshot> farmer = farmers.find(telegramUserId);
        return new MiniAppContext(farmer.map(this::maxHa).orElse(null),
                farmer.map(FarmerSnapshot::mainCrop).map(Enum::name).orElse(null),
                fields.rings(telegramUserId, null).stream().limit(60).map(Polyline::encode).toList());
    }

    /** Inline "🗺 Dalani xaritada belgilash" — Mini App HTTP orqali yuboradi; sozlanmagan bo'lsa null */
    public InlineKeyboardMarkup mapInline(long chatId, String lang) {
        return keyboards.mapInline(lang, maxHa(chatId), farmers.find(chatId).map(FarmerSnapshot::mainCrop).orElse(null));
    }

    public BigDecimal maxHa(long telegramUserId) {
        return maxHa(telegramUserId, null);
    }

    /** Tahrirlash: dalaning o'z maydoni band hisoblanmaydi (o'rniga yangi chegara keladi) */
    public BigDecimal maxHa(long telegramUserId, Long editFieldId) {
        return farmers.find(telegramUserId).map(f -> maxHa(f, editFieldId)).orElse(null);
    }

    private BigDecimal maxHa(FarmerSnapshot f) {
        return maxHa(f, null);
    }

    /** Mini App'dagi ogohlantirish chegarasi: bitta dala limiti va tarifdagi jami qoldiq (faqat UX — qaror serverda) */
    private BigDecimal maxHa(FarmerSnapshot f, Long editFieldId) {
        var plan = plans.effective(f.id(), LocalDate.now());
        // Cheksiz tarifda band maydon so'rovi kerak emas (menyu deyarli har javobga biriktiriladi)
        return plan.maxTotalHa() == null ? plan.maxFieldHa() : plan.allowedHa(fields.usedHa(f.id(), editFieldId));
    }
}
