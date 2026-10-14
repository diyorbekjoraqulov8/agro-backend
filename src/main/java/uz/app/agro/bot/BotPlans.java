package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.billing.dto.PlanResponse;
import uz.app.agro.farmer.FarmerRegistrationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bo'lim "💎 Tariflar": bazadagi faol tariflar (admin o'zgartirsa — bot ham), fermerning joriy tarifi belgilanadi,
 * bepul foydalanuvchiga pullik tarif nima berishi tushuntiriladi.
 * callback_data: pl:list — ro'yxat; pl:buy:<KOD> — so'rov (BotUpdateHandler: murojaat ochadi, to'lov hozircha qo'lda).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotPlans {

    static final String PLANS = "pl:", LIST = PLANS + "list", BUY = PLANS + "buy:";

    private final BotSender sender;
    private final BotMessages messages;
    private final PlanService plans;
    private final FarmerRegistrationService farmers;

    public void showCatalog(long tg, long chatId, String lang) {
        EffectivePlan current = farmers.find(tg).map(f -> plans.effective(f.id(), LocalDate.now())).orElse(null);
        List<PlanResponse> catalog = plans.catalog();
        List<String> blocks = new ArrayList<>();
        blocks.add(messages.text(lang, "plans.title"));
        for (PlanResponse p : catalog) blocks.add(describe(lang, p, current));
        Optional<PlanResponse> upgrade = upgrade(catalog);
        // Bepul foydalanuvchiga — pullik tarif foydasi (cheklov raqamlari yuqorida, bu yerda — nima uchun kerakligi)
        if (current != null && !current.paid() && upgrade.isPresent()) {
            blocks.add(messages.text(lang, "plans.why", upgrade.get().name()));
        }
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        for (PlanResponse p : catalog) {
            if (p.systemPlan()) continue;
            boolean mine = current != null && current.paid() && p.code().equals(current.planCode());
            kb.addRow(new InlineKeyboardButton(messages.text(lang, mine ? "plans.extend" : "plans.buy", p.name()))
                    .callbackData(BUY + p.code()));
        }
        sender.send(chatId, String.join("\n\n", blocks), kb.inlineKeyboard().length == 0 ? null : kb);
    }

    /** "Mening tarifim" ostidagi tugmalar: barcha tariflar + (bepul bo'lsa) eng arzon pullik tarifni olish */
    public InlineKeyboardMarkup planButtons(String lang, EffectivePlan current) {
        return planButtons(lang, current.paid());
    }

    /** @param paid — pullik tarifda: faqat "Barcha tariflar"; bepul — yana eng arzon pullik tarifni olish */
    public InlineKeyboardMarkup planButtons(String lang, boolean paid) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(
                new InlineKeyboardButton(messages.text(lang, "settings.plans")).callbackData(LIST));
        if (!paid) {
            upgrade(plans.catalog()).ifPresent(p -> kb.addRow(
                    new InlineKeyboardButton(messages.text(lang, "plans.buy", p.name())).callbackData(BUY + p.code())));
        }
        return kb;
    }

    /** So'ralgan tarif — faqat faol pullik (callback_data ishonchsiz) */
    public Optional<PlanResponse> purchasable(String code) {
        return plans.catalog().stream().filter(p -> !p.systemPlan() && p.code().equals(code)).findFirst();
    }

    /** "30 000 so'm / 30 kun" */
    public String price(String lang, PlanResponse p) {
        if (p.price().signum() == 0) return messages.text(lang, "plans.price.free");
        return messages.text(lang, "plans.price", money(p.price()), currency(lang, p.currency()),
                String.valueOf(p.durationDays()));
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    private String describe(String lang, PlanResponse p, EffectivePlan current) {
        boolean mine = current != null && p.code().equals(current.planCode());
        String ha = p.maxFieldHa().stripTrailingZeros().toPlainString();
        String fields = p.maxFields() == null ? messages.text(lang, "plans.fields.unlimited", ha)
                : messages.text(lang, "plans.fields", String.valueOf(p.maxFields()), ha);
        String analyses = p.maxAnalyses() == null ? messages.text(lang, "plans.analyses.unlimited")
                : messages.text(lang, p.systemPlan() ? "plans.analyses.lifetime" : "plans.analyses", String.valueOf(p.maxAnalyses()));
        StringBuilder b = new StringBuilder()
                .append(p.systemPlan() ? "🆓 " : "💎 ").append(p.name())
                .append(p.price().signum() == 0 ? "" : " — " + price(lang, p))
                .append(mine ? "  " + messages.text(lang, "plans.current") : "")
                .append("\n• ").append(fields)
                .append("\n• ").append(analyses);
        // description ko'rsatilmaydi — u admin uchun ichki izoh ("To'lov qilinmagan fermerlar uchun")
        return b.toString();
    }

    /** Bepul foydalanuvchiga taklif qilinadigan — eng arzon pullik tarif (catalog narx bo'yicha tartiblangan) */
    private static Optional<PlanResponse> upgrade(List<PlanResponse> catalog) {
        return catalog.stream().filter(p -> !p.systemPlan()).findFirst();
    }

    private String currency(String lang, String code) {
        return "UZS".equals(code) ? messages.text(lang, "currency.UZS") : code;
    }

    /** 30000.00 → "30 000" (MessageFormat'ga String beramiz — u o'z formatini qo'ymasin) */
    private static String money(BigDecimal v) {
        String digits = v.setScale(0, RoundingMode.HALF_UP).toPlainString();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }
}
