package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.billing.PlanService;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.farmer.FarmerSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    // Shaxsiy chatda chat_id == telegram_user_id (bot faqat shaxsiy chatda ishlaydi)
    public ReplyKeyboardMarkup keyboard(long chatId, String lang) {
        return keyboards.mainMenu(lang, maxHa(chatId), farmers.find(chatId).map(FarmerSnapshot::mainCrop).orElse(null));
    }

    /** Inline "🗺 Dalani xaritada belgilash" — Mini App HTTP orqali yuboradi; sozlanmagan bo'lsa null */
    public InlineKeyboardMarkup mapInline(long chatId, String lang) {
        return keyboards.mapInline(lang, maxHa(chatId), farmers.find(chatId).map(FarmerSnapshot::mainCrop).orElse(null));
    }

    public BigDecimal maxHa(long telegramUserId) {
        return farmers.find(telegramUserId).map(f -> plans.effective(f.id(), LocalDate.now()).maxFieldHa()).orElse(null);
    }
}
