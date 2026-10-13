package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.farmer.FarmerSnapshot;
import uz.app.agro.field.FieldBotService;
import uz.app.agro.referral.ReferralService;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Bo'limlar: "🎁 Do'stlarni taklif" va "⚙️ Sozlamalar" (💳 Mening tarifim, 🌐 Til, 📋 Barcha tariflar — BotPlans). Taklif xabarlari — ReferralRewarder (outbox).
 * callback_data: st:plan, st:lang
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotReferral {

    static final String SETTINGS = "st:";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BotSender sender;
    private final BotMessages messages;
    private final BotKeyboards keyboards;
    private final BotIdentity identity;
    private final ReferralService referrals;
    private final PlanService plans;
    private final FarmerRegistrationService farmers;
    private final FieldBotService fields;
    private final BotPlans planSection;

    public void showFriends(long tg, long chatId, String lang) {
        var s = referrals.summary(tg).orElse(null);
        if (s == null) return;
        if (!s.enabled()) {
            sender.send(chatId, messages.text(lang, "ref.disabled"), null);
            return;
        }
        String username = identity.username();
        String link = username == null ? null : "https://t.me/" + username + "?start=" + ReferralService.START_PREFIX + s.code();
        // Raqamlar String — MessageFormat 1000 ni "1 000" qilmasin
        String text = messages.text(lang, "ref.info", String.valueOf(s.required()), String.valueOf(s.rewardDays()),
                s.rewardPlanName(), link != null ? link : "—", String.valueOf(s.invited()), String.valueOf(s.activated()),
                String.valueOf(Math.max(0, s.required() - s.progress())), String.valueOf(s.rewards()));
        InlineKeyboardMarkup kb = null;
        if (link != null) {
            String share = "https://t.me/share/url?url=" + enc(link) + "&text=" + enc(messages.text(lang, "ref.shareText"));
            kb = new InlineKeyboardMarkup(new InlineKeyboardButton(messages.text(lang, "ref.shareButton")).url(share));
        }
        sender.send(chatId, text, kb);
    }

    public void showSettings(long chatId, String lang) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(
                new InlineKeyboardButton(messages.text(lang, "settings.plan")).callbackData(SETTINGS + "plan"),
                new InlineKeyboardButton(messages.text(lang, "settings.language")).callbackData(SETTINGS + "lang"));
        // st:profile — BotUpdateHandler'da (savollar oqimi o'sha yerda)
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "settings.plans")).callbackData(BotPlans.LIST));
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "settings.profile")).callbackData(SETTINGS + "profile"));
        sender.send(chatId, messages.text(lang, "settings.title"), kb);
    }

    /** @return true — bu bo'lim tugmasi edi */
    public boolean onCallback(String callbackId, long tg, long chatId, String lang, String data) {
        if (!data.startsWith(SETTINGS)) return false;
        sender.answer(callbackId, null);
        switch (data.substring(SETTINGS.length())) {
            case "plan" -> showPlan(tg, chatId, lang);
            case "lang" -> sender.send(chatId, messages.text(lang, "lang.choose"), keyboards.languages());
            default -> { }
        }
        return true;
    }

    private void showPlan(long tg, long chatId, String lang) {
        FarmerSnapshot f = farmers.find(tg).orElse(null);
        if (f == null) return;
        EffectivePlan p = plans.effective(f.id(), LocalDate.now());
        String status = p.paid() ? messages.text(lang, "plan.paidUntil", p.validUntil().format(DAY)) : messages.text(lang, "plan.freeHint");
        String analyses = p.maxAnalyses() == null ? messages.text(lang, "plan.unlimited")
                : p.usedAnalyses() + " / " + p.maxAnalyses() + (p.paid() ? "" : " " + messages.text(lang, "plan.lifetime"));
        sender.send(chatId, messages.text(lang, "plan.info", p.planName(), status,
                String.valueOf(fields.list(tg).size()),
                p.maxFields() == null ? messages.text(lang, "plan.unlimited") : String.valueOf(p.maxFields()),
                ha(lang, p.maxFieldHa()), analyses), planSection.planButtons(lang, p));
    }

    private static String ha(String lang, BigDecimal v) {
        if (v == null) return "—";
        String s = v.stripTrailingZeros().toPlainString();
        return "en".equals(lang) ? s : s.replace('.', ',');
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
