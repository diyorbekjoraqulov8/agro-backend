package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.billing.DiscountPricing;
import uz.app.agro.billing.SeasonOffers;
import uz.app.agro.billing.dto.SeasonOffer;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.dto.PlanPrice;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.billing.dto.PlanResponse;
import uz.app.agro.common.Money;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bo'lim "💎 Tariflar": bazadagi faol tariflar (admin o'zgartirsa — bot ham), fermerning joriy tarifi belgilanadi,
 * bepul foydalanuvchiga pullik tarif nima berishi tushuntiriladi.
 * callback_data: pl:list — ro'yxat; pl:buy:<KOD> — so'rov (BotUpdateHandler: murojaat ochadi, to'lov hozircha qo'lda);
 * "narx kelishiladi" tarifi (Agro Biznes, contactSales) — bz:start (BotBusiness: qisqa ariza), egasi uzaytirsa — pl:buy.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotPlans {

    static final String PLANS = "pl:", LIST = PLANS + "list", BUY = PLANS + "buy:", SEASON = PLANS + "season";

    private final BotSender sender;
    private final BotMessages messages;
    private final PlanService plans;
    private final FarmerRegistrationService farmers;
    private final SettingsService settings;
    private final DiscountPricing pricing;
    private final SeasonOffers seasons;

    public void showCatalog(long tg, long chatId, String lang) {
        var farmer = farmers.find(tg);
        EffectivePlan current = farmer.map(f -> plans.effective(f.id(), LocalDate.now())).orElse(null);
        List<PlanResponse> catalog = plans.catalog();
        // Fermerga mos chegirma (eng foydalisi) — narx yonida ko'rsatiladi
        Map<Long, PlanPrice> prices = farmer.map(f -> pricing.prices(f.id(), catalog, LocalDate.now())).orElse(Map.of());
        List<String> blocks = new ArrayList<>();
        blocks.add(messages.text(lang, "plans.title"));
        for (PlanResponse p : catalog) blocks.add(describe(lang, p, current, prices.get(p.id())));
        // Mavsumiy obuna (Sozlamalar) — alohida taklif: mavsum oxirigacha bir to'lov, foiz chegirma bilan
        // Cheksiz tarif (Agro Biznes) egasiga pastroq tarif — mavsumiy ham — taklif qilinmaydi
        boolean unlimited = current != null && current.paid() && current.maxTotalHa() == null;
        Optional<SeasonOffer> season = unlimited ? Optional.empty() : farmer.flatMap(f -> seasons.offer(f.id(), LocalDate.now()));
        season.ifPresent(o -> blocks.add(seasonText(lang, o)));
        Optional<PlanResponse> upgrade = upgrade(catalog);
        // Bepul foydalanuvchiga — pullik tarif foydasi (cheklov raqamlari yuqorida, bu yerda — nima uchun kerakligi)
        if (current != null && !current.paid() && upgrade.isPresent()) {
            blocks.add(messages.text(lang, "plans.why", upgrade.get().name()));
        }
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        for (PlanResponse p : catalog) {
            if (p.systemPlan()) continue;
            boolean mine = current != null && current.paid() && p.code().equals(current.planCode());
            // Cheksiz maydonli tarif egasiga (Agro Biznes) pastroq tarifni taklif qilmaymiz — faqat o'zinikini uzaytirish
            if (!mine && current != null && current.paid() && current.maxTotalHa() == null) continue;
            if (p.contactSales() && !mine) {
                kb.addRow(new InlineKeyboardButton(messages.text(lang, "plans.apply", p.name())).callbackData(BotBusiness.START));
                continue;
            }
            kb.addRow(new InlineKeyboardButton(messages.text(lang, mine ? "plans.extend" : "plans.buy", p.name()))
                    .callbackData(BUY + p.code()));
        }
        season.ifPresent(o -> kb.addRow(new InlineKeyboardButton(messages.text(lang, "plans.season.buy")).callbackData(SEASON)));
        // Obuna bo'lmoqchi bo'lgan payt — do'stlarni taklif qilib bepul olish yo'li ham (st:friends — BotReferral)
        if (current != null && settings.getBool(Setting.REFERRAL_ENABLED)) {
            kb.addRow(new InlineKeyboardButton(messages.text(lang, current.paid() ? "settings.friends" : "plans.friendsAlt"))
                    .callbackData(BotReferral.SETTINGS + "friends"));
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

    /** Chegirma bilan: "59 000 → 47 200 so'm / 30 kun" (price null yoki chegirmasiz — oddiy narx) */
    public String price(String lang, PlanResponse p, PlanPrice price) {
        if (price == null || !price.discounted() || p.contactSales()) return price(lang, p);
        return messages.text(lang, "plans.price.discounted", money(price.basePrice()), money(price.finalPrice()),
                currency(lang, p.currency()), String.valueOf(p.durationDays()));
    }

    /** "Navro'z aksiyasi: −20%, 31-martgacha" */
    public String discountLine(String lang, PlanPrice price) {
        return messages.text(lang, price.discountEndsOn() == null ? "plans.discount" : "plans.discount.until",
                price.discountName(), price.discountLabel(), BotMessages.date(lang, price.discountEndsOn()));
    }

    /** "🌱 Mavsumiy Pro — 31-oktabrgacha (5 oy): 295 000 → 236 000 so'm (−20%)" */
    public String seasonText(String lang, SeasonOffer o) {
        String text = messages.text(lang, "plans.season", o.planName(), BotMessages.date(lang, o.until()), String.valueOf(o.days()),
                money(o.basePrice()), money(o.finalPrice()), currency(lang, o.currency()), String.valueOf(o.discountPct()));
        // Joriy obunasi bor — mavsum o'sha tugagach boshlanadi: fermer qachondan ekanini bilsin
        return o.from().isAfter(LocalDate.now()) ? text + "\n" + messages.text(lang, "plans.season.from", BotMessages.date(lang, o.from())) : text;
    }

    public Optional<SeasonOffer> seasonOffer(long tg) {
        return farmers.find(tg).flatMap(f -> seasons.offer(f.id(), LocalDate.now()));
    }

    /** Fermer uchun narx (chegirma bilan) — tarif so'rovi xodimlarga shu narx bilan boradi */
    public PlanPrice priceFor(long tg, PlanResponse p) {
        return farmers.find(tg).map(f -> pricing.price(f.id(), p, LocalDate.now())).orElse(null);
    }

    /** "30 000 so'm / 30 kun" */
    public String price(String lang, PlanResponse p) {
        if (p.contactSales()) return messages.text(lang, "plans.price.contact");
        if (p.price().signum() == 0) return messages.text(lang, "plans.price.free");
        return messages.text(lang, "plans.price", money(p.price()), currency(lang, p.currency()),
                String.valueOf(p.durationDays()));
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    private String describe(String lang, PlanResponse p, EffectivePlan current, PlanPrice price) {
        boolean mine = current != null && p.code().equals(current.planCode());
        String area = p.maxTotalHa() == null ? messages.text(lang, "plans.area.unlimited")
                : messages.text(lang, "plans.area", haText(p.maxTotalHa()));
        String fields = p.maxFields() == null ? messages.text(lang, "plans.fields.any")
                : messages.text(lang, "plans.fields.max", String.valueOf(p.maxFields()));
        String analyses = p.maxAnalyses() == null ? messages.text(lang, "plans.analyses.unlimited")
                : messages.text(lang, p.systemPlan() ? "plans.analyses.lifetime" : "plans.analyses", String.valueOf(p.maxAnalyses()));
        StringBuilder b = new StringBuilder()
                .append(p.systemPlan() ? "🆓 " : p.contactSales() ? "🏢 " : "💎 ").append(name(lang, p.code(), p.name()))
                .append(p.price().signum() == 0 && !p.contactSales() ? "" : " — " + price(lang, p, price))
                .append(mine ? "  " + messages.text(lang, "plans.current") : "")
                .append("\n• ").append(area)
                .append("\n• ").append(fields)
                .append("\n• ").append(analyses);
        if (p.contactSales()) b.append("\n• ").append(messages.text(lang, "plans.business.extra"));
        if (price != null && price.discounted()) b.append("\n🏷 ").append(discountLine(lang, price));
        // description ko'rsatilmaydi — u admin uchun ichki izoh ("To'lov qilinmagan fermerlar uchun")
        return b.toString();
    }

    /** Tizim tarifi nomi (admin o'zgartirmagan "Bepul") — fermer tilida; qolgani bazadagidek (Pro, Agro Biznes — nom) */
    public String name(String lang, String code, String dbName) {
        if ("FREE".equals(code) && messages.text("uz", "plans.freeName").equals(dbName)) return messages.text(lang, "plans.freeName");
        return dbName;
    }

    /**
     * Limitga yetganda: qaysi tarif ko'proq maydon beradi — keyingi pullik (narxli) yoki "narx kelishiladi" (Agro Biznes).
     * Cheksiz tarifda yoki kattaroq tarif yo'q bo'lsa — bo'sh
     */
    public Optional<String> upgradeLine(String lang, EffectivePlan current) {
        if (current.maxTotalHa() == null) return Optional.empty();
        return plans.catalog().stream()
                .filter(p -> !p.systemPlan() && !p.code().equals(current.planCode()))
                .filter(p -> p.maxTotalHa() == null || p.maxTotalHa().compareTo(current.maxTotalHa()) > 0)
                .findFirst()
                .map(p -> p.contactSales()
                        ? messages.text(lang, "polygon.businessLine", p.name())
                        : messages.text(lang, "polygon.upgradeLine", p.name(),
                                p.maxTotalHa() == null ? messages.text(lang, "plan.unlimited") : haText(p.maxTotalHa())));
    }

    private static String haText(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** Bepul fermerga taklif qilinadigan tarif (Sozlamalar tepasidagi "💎 ... ga o'tish") */
    public Optional<PlanResponse> upgradeOffer() {
        return upgrade(plans.catalog());
    }

    /** Bepul foydalanuvchiga taklif qilinadigan — eng arzon narxli pullik tarif ("narx kelishiladi" emas) */
    private static Optional<PlanResponse> upgrade(List<PlanResponse> catalog) {
        return catalog.stream().filter(p -> !p.systemPlan() && !p.contactSales()).findFirst();
    }

    private String currency(String lang, String code) {
        return "UZS".equals(code) ? messages.text(lang, "currency.UZS") : code;
    }

    /** 30000.00 → "30 000" (MessageFormat'ga String beramiz — u o'z formatini qo'ymasin) */
    private static String money(BigDecimal v) {
        return Money.digits(v);
    }
}
