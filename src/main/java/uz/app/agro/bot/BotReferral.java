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
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * "⚙️ Sozlamalar" — asosiy menyudan olib tashlangan hamma narsa shu yerda: 💳 Mening tarifim, 🌐 Til, 📋 Barcha tariflar
 * (BotPlans), 📝 Ma'lumotlarim, 🎁 Do'stlarni taklif, 💬 Fikr va yordam, ❓ Qo'llanma. Bepul fermerga — Pro taklifi tepada.
 * Taklif xabarlari — ReferralRewarder (outbox).
 * callback_data: st:plan, st:lang, st:friends (shu yerda); st:profile, st:support, st:help — BotUpdateHandler'da
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotReferral {

    static final String SETTINGS = "st:";

    private final BotSender sender;
    private final BotMessages messages;
    private final BotKeyboards keyboards;
    private final BotIdentity identity;
    private final ReferralService referrals;
    private final PlanService plans;
    private final FarmerRegistrationService farmers;
    private final FieldBotService fields;
    private final BotPlans planSection;
    private final SettingsService settings;

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

    public void showSettings(long tg, long chatId, String lang) {
        EffectivePlan p = farmers.find(tg).map(f -> plans.effective(f.id(), LocalDate.now())).orElse(null);
        var upgrade = planSection.upgradeOffer();
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        String text = messages.text(lang, "settings.title");
        // Abonent bo'lmaganga Pro taklifi — har safar, eng tepada (asosiy menyuda alohida "Pro" tugmasi yo'q)
        if (p != null && !p.paid() && upgrade.isPresent()) {
            text += "\n\n" + messages.text(lang, "settings.freeLine", upgrade.get().name());
            kb.addRow(new InlineKeyboardButton(messages.text(lang, "settings.upgrade", upgrade.get().name()))
                    .callbackData(BotPlans.BUY + upgrade.get().code()));
        } else if (p != null && p.paid()) {
            text += "\n\n" + messages.text(lang, "settings.paidLine", p.planName(), BotMessages.date(lang, p.validUntil()));
        }
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "settings.plan")).callbackData(SETTINGS + "plan"),
                new InlineKeyboardButton(messages.text(lang, "settings.plans")).callbackData(BotPlans.LIST));
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "settings.profile")).callbackData(SETTINGS + "profile"));
        kb.addRow(new InlineKeyboardButton(messages.text(lang, "settings.language")).callbackData(SETTINGS + "lang"),
                new InlineKeyboardButton(messages.text(lang, "settings.help")).callbackData(SETTINGS + "help"));
        InlineKeyboardButton support = new InlineKeyboardButton(messages.text(lang, "settings.feedback")).callbackData(SETTINGS + "support");
        InlineKeyboardButton friends = friendsButton(lang);
        if (friends != null) kb.addRow(friends, support);
        else kb.addRow(support);
        sender.send(chatId, text, kb);
    }

    /** Kerakli paytda (tarif so'rovi, "Mening tarifim") chiqadigan "🎁 Do'stlarni taklif" tugmasi; dastur o'chiq — null */
    public InlineKeyboardButton friendsButton(String lang) {
        return settings.getBool(Setting.REFERRAL_ENABLED)
                ? new InlineKeyboardButton(messages.text(lang, "settings.friends")).callbackData(SETTINGS + "friends") : null;
    }

    /** @return true — bu bo'lim tugmasi edi */
    public boolean onCallback(String callbackId, long tg, long chatId, String lang, String data) {
        if (!data.startsWith(SETTINGS)) return false;
        sender.answer(callbackId, null);
        switch (data.substring(SETTINGS.length())) {
            case "plan" -> showPlan(tg, chatId, lang);
            case "lang" -> sender.send(chatId, messages.text(lang, "lang.choose"), keyboards.languages());
            case "friends" -> showFriends(tg, chatId, lang);
            default -> { }
        }
        return true;
    }

    private void showPlan(long tg, long chatId, String lang) {
        FarmerSnapshot f = farmers.find(tg).orElse(null);
        if (f == null) return;
        EffectivePlan p = plans.effective(f.id(), LocalDate.now());
        String status = p.paid() ? messages.text(lang, "plan.paidUntil", BotMessages.date(lang, p.validUntil())) : messages.text(lang, "plan.freeHint");
        String analyses = p.maxAnalyses() == null ? messages.text(lang, "plan.unlimited")
                : p.usedAnalyses() + " / " + p.maxAnalyses() + (p.paid() ? "" : " " + messages.text(lang, "plan.lifetime"));
        InlineKeyboardMarkup kb = planSection.planButtons(lang, p);
        InlineKeyboardButton friends = friendsButton(lang);
        BigDecimal used = fields.usedHa(f.id(), null);
        String area = p.maxTotalHa() == null ? messages.text(lang, "plan.area.unlimited", ha(lang, used))
                : messages.text(lang, "plan.area", ha(lang, used), ha(lang, p.maxTotalHa()));
        String text = messages.text(lang, "plan.info", planSection.name(lang, p.planCode(), p.planName()), status,
                String.valueOf(fields.list(tg).size()),
                p.maxFields() == null ? messages.text(lang, "plan.unlimited") : String.valueOf(p.maxFields()),
                area, analyses);
        // "🎁 ... 👇" qatori faqat tugma bor bo'lsa (dastur o'chiq — yo'q tugmaga ishora qilmasin)
        if (friends != null) {
            text += "\n\n" + messages.text(lang, "plan.friendsLine");
            kb.addRow(friends);
        }
        sender.send(chatId, text, kb);
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
