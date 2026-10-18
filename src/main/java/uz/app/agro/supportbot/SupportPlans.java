package uz.app.agro.supportbot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import uz.app.agro.billing.BillingService;
import uz.app.agro.billing.DiscountPricing;
import uz.app.agro.billing.SeasonOffers;
import uz.app.agro.billing.dto.SeasonOffer;
import uz.app.agro.billing.dto.PlanPrice;
import uz.app.agro.billing.PaymentMethod;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.dto.PaymentRequest;
import uz.app.agro.billing.dto.PlanResponse;
import uz.app.agro.common.HumanDates;
import uz.app.agro.common.Money;
import uz.app.agro.rbac.Permissions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Guruhdan tarif berish (admin panelga o'tmasdan): [💳 Tarif berish] yoki /tarif → tarif → davr soni → to'lov usuli →
 * (narx kelishiladi bo'lsa — summa matn bilan) → [✅ Tasdiqlash] → BillingService.recordPayment SHU xodim nomidan
 * (billing:manage — @PreAuthorize). Fermer — tugma turgan MAVZUdan (callback_data'da fermer yo'q). Summa ham
 * callback_data'da emas: narxli tarifda — narx × davr (serverda), kelishilganda — server xotirasida (tasdiq xabari bo'yicha).
 * callback_data: sb:p · sb:pp:<plan> · sb:pm:<plan>:<n> · sb:pc:<plan>:<n>:<USUL> · sb:pk:<plan>:<n>:<USUL> · sb:px
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank()")
public class SupportPlans {

    static final String OPEN = "sb:p", PLAN = "sb:pp:", PERIODS = "sb:pm:", METHOD = "sb:pc:", CONFIRM = "sb:pk:", CANCEL = "sb:px";
    private static final int[] PERIOD_OPTIONS = {1, 3, 6, 12};
    /** "Mavsum oxirigacha" (SeasonOffers) — davrlar soni o'rniga belgi */
    private static final int SEASON = 99;
    private static final List<PaymentMethod> METHODS = List.of(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER, PaymentMethod.OTHER);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999");
    private static final long AMOUNT_TTL_SECONDS = 600;
    // Summa deb faqat raqamga o'xshash matn olinadi — xodimning oddiy javobi fermerga ketaveradi
    // Kamida 3 ta raqam — "2", "..." kabi qisqa javob fermerga ketaversin
    private static final Pattern AMOUNT_LIKE = Pattern.compile("^(?=(?:\\D*\\d){3})[\\d\\s .,]+(\\s*(so['ʻ’]?m|сум|sum|uzs|usd|\\$))?\\.?$", Pattern.CASE_INSENSITIVE);

    private final SupportBotClient client;
    private final SupportStore store;
    private final SupportActions actions;
    private final PlanService plans;
    private final BillingService billing;
    private final DiscountPricing pricing;
    private final SeasonOffers seasons;

    /** Kelishilgan summa kutilmoqda: mavzu + xodim → qaysi tasdiq xabari */
    private record AwaitAmount(long planId, int periods, PaymentMethod method, int messageId, Instant expires) {}

    private final Map<String, AwaitAmount> awaiting = new ConcurrentHashMap<>();
    /** Kelishilgan summa — tasdiq xabari (chat:message) bo'yicha; callback_data'da summa yo'q. 1 soatdan keyin eskiradi */
    private record Agreed(BigDecimal amount, Instant expires) {}

    private final Map<String, Agreed> agreed = new ConcurrentHashMap<>();
    /** Tasdiqlangan xabarlar — ikki marta bosilsa ikki to'lov yozilmasin (pengrad bitta thread — ketma-ket keladi) */
    private final Set<String> confirmed = ConcurrentHashMap.newKeySet();

    boolean isMine(String data) {
        return data.equals(OPEN) || data.startsWith(PLAN) || data.startsWith(PERIODS) || data.startsWith(METHOD)
                || data.startsWith(CONFIRM) || data.equals(CANCEL);
    }

    /** /tarif buyrug'i — mavzuda tarif ro'yxati */
    void command(Message m, SupportStore.Topic topic) {
        if (billingStaff(m.from().id()).isEmpty()) {
            client.send(m.chat().id(), m.messageThreadId(), actions.text("support.plan.denied"), null);
            return;
        }
        client.send(m.chat().id(), m.messageThreadId(), actions.text("support.plan.choose", topic.farmerName()), planButtons());
    }

    /**
     * Mavzuda xodim yozgan matn — kutilayotgan summa bo'lsa iste'mol qiladi (fermerga YUBORILMAYDI).
     * @return true — bu summa edi
     */
    boolean onText(Message m, SupportStore.Topic topic, long thread, String text) {
        String key = key(thread, m.from().id());
        AwaitAmount a = awaiting.get(key);
        if (a == null) return false;
        if (a.expires().isBefore(Instant.now())) {
            awaiting.remove(key);
            return false;
        }
        if (!AMOUNT_LIKE.matcher(text).matches()) return false;          // oddiy javob — fermerga
        BigDecimal amount = parseAmount(text);
        if (amount == null) {
            client.send(m.chat().id(), thread, actions.text("support.plan.amountInvalid"), null);
            return true;
        }
        PlanResponse p = plan(a.planId()).orElse(null);
        awaiting.remove(key);
        if (p == null) {
            client.edit(m.chat().id(), a.messageId(), actions.text("support.plan.gone"), null);
            return true;
        }
        Instant now = Instant.now();
        agreed.values().removeIf(x -> x.expires().isBefore(now));
        agreed.put(m.chat().id() + ":" + a.messageId(), new Agreed(amount, now.plusSeconds(3600)));
        client.edit(m.chat().id(), a.messageId(), confirmText(topic, p, a.periods(), a.method(), amount),
                confirmButtons(p, a.periods(), a.method()));
        return true;
    }

    void onCallback(CallbackQuery cb, long thread, SupportStore.Topic topic) {
        long chatId = cb.maybeInaccessibleMessage().chat().id();
        int messageId = cb.maybeInaccessibleMessage().messageId();
        String data = cb.data();
        SupportStore.Staff staff = billingStaff(cb.from().id()).orElse(null);
        if (staff == null) {
            client.answer(cb.id(), actions.text("support.plan.denied"));
            return;
        }
        client.answer(cb.id(), null);
        String[] a = data.split(":");
        try {
            if (data.equals(OPEN)) {
                client.send(chatId, thread, actions.text("support.plan.choose", topic.farmerName()), planButtons());
            } else if (data.equals(CANCEL)) {
                // Bu xabarni kim kutayotgan bo'lsa ham — kutish tugaydi (boshqa xodim bekor qilgan bo'lishi mumkin)
                awaiting.values().removeIf(x -> x.messageId() == messageId);
                agreed.remove(chatId + ":" + messageId);
                client.edit(chatId, messageId, actions.text("support.plan.cancelled"), null);
            } else if (data.startsWith(PLAN)) {
                PlanResponse p = plan(Long.parseLong(a[2])).orElse(null);
                if (p == null) return;
                client.edit(chatId, messageId, actions.text("support.plan.periods", p.name()), periodButtons(p, topic));
            } else if (data.startsWith(PERIODS)) {
                PlanResponse p = plan(Long.parseLong(a[2])).orElse(null);
                int n = periods(a[3]);
                if (p == null || n == 0) return;
                if (n == SEASON && season(topic, p).isEmpty()) return;
                client.edit(chatId, messageId, actions.text("support.plan.method", p.name(), periodLabel(p, n)), methodButtons(p, n));
            } else if (data.startsWith(METHOD)) {
                PlanResponse p = plan(Long.parseLong(a[2])).orElse(null);
                int n = periods(a[3]);
                PaymentMethod method = method(a[4]);
                if (p == null || n == 0 || method == null) return;
                if (n == SEASON && (p.contactSales() || season(topic, p).isEmpty())) return;
                if (p.contactSales()) {
                    Instant now = Instant.now();
                    awaiting.values().removeIf(x -> x.expires().isBefore(now));
                    awaiting.put(key(thread, cb.from().id()), new AwaitAmount(p.id(), n, method, messageId, now.plusSeconds(AMOUNT_TTL_SECONDS)));
                    client.edit(chatId, messageId, actions.text("support.plan.amount", p.name(), periodLabel(p, n), currency(p)),
                            new InlineKeyboardMarkup(new InlineKeyboardButton(actions.text("support.plan.cancel")).callbackData(CANCEL)));
                } else {
                    BigDecimal amount = n == SEASON ? season(topic, p).map(SeasonOffer::finalPrice).orElse(null)
                            : unitPrice(topic, p).finalPrice().multiply(BigDecimal.valueOf(n));
                    if (amount == null) return;
                    if (amount.compareTo(MAX_AMOUNT) > 0) {
                        client.edit(chatId, messageId, actions.text("support.plan.tooMuch"), null);
                        return;
                    }
                    client.edit(chatId, messageId, confirmText(topic, p, n, method, amount), confirmButtons(p, n, method));
                }
            } else if (data.startsWith(CONFIRM)) {
                confirm(chatId, messageId, staff, topic, a);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // soxta callback_data — jim (javob allaqachon berilgan)
        } catch (RuntimeException e) {
            log.warn("support bot: tarif berish qadami bajarilmadi ({})", e.getClass().getSimpleName());
            client.send(chatId, thread, "⚠️ " + actions.text("support.failed"), null);
        }
    }

    private void confirm(long chatId, int messageId, SupportStore.Staff staff, SupportStore.Topic topic, String[] a) {
        PlanResponse p = plan(Long.parseLong(a[2])).orElse(null);
        int n = periods(a[3]);
        PaymentMethod method = method(a[4]);
        if (p == null || n == 0 || method == null) return;
        String msgKey = chatId + ":" + messageId;
        if (n == SEASON) {
            confirmSeason(chatId, messageId, msgKey, staff, topic, p, method);
            return;
        }
        // Summa: narxli — serverda qayta hisoblanadi (chegirma bilan); kelishilgan — xodim yozgan (xotirada), > 0 bo'lishi shart
        PlanPrice unit = p.contactSales() ? null : unitPrice(topic, p);
        Long discountId = unit == null ? null : unit.discountId();
        Agreed typed = p.contactSales() ? agreed.get(msgKey) : null;
        if (p.contactSales() && (typed == null || typed.expires().isBefore(Instant.now()))) return;
        BigDecimal amount = p.contactSales() ? typed.amount() : unit.finalPrice().multiply(BigDecimal.valueOf(n));
        if ((p.contactSales() && amount.signum() <= 0) || amount.compareTo(MAX_AMOUNT) > 0) return;
        if (!confirmed.add(msgKey)) return;
        if (confirmed.size() > 10_000) confirmed.clear();          // jarayon uzoq ishlasa — xotira o'smasin
        String error = actions.runAs(staff, me -> billing.recordPayment(topic.farmerId(), new PaymentRequest(p.id(), amount,
                null, null, method, null, null, "Telegram support guruhidan: " + periodLabel(p, n), n, discountId)));
        if (error != null) {
            confirmed.remove(msgKey);                                // xato — tugmalar qoladi, qayta bosish mumkin
            client.edit(chatId, messageId, "⚠️ " + error + "\n\n" + confirmText(topic, p, n, method, amount), confirmButtons(p, n, method));
            return;
        }
        agreed.remove(msgKey);
        SupportStore.Period period = store.currentUntil(topic.farmerId());
        // Yozilgan summa ham — ko'rsatilgandan farq qilsa (chegirma shu orada tugagan) xodim ko'radi
        client.edit(chatId, messageId, actions.text("support.plan.done", p.name(),
                period == null ? "—" : HumanDates.format("uz", period.until()), staff.user().email()) + "\n💰 " + money(amount, p), null);
    }

    /** Mavsumiy: summa va davr — SeasonOffers'dan (tasdiq paytida qayta), chegirmalar qo'shilmaydi */
    private void confirmSeason(long chatId, int messageId, String msgKey, SupportStore.Staff staff, SupportStore.Topic topic,
                               PlanResponse p, PaymentMethod method) {
        // Ikkinchi bosish — jim (birinchisidan keyin obuna mavsum oxirigacha, taklif yo'q: muvaffaqiyat xabari o'chmasin)
        if (confirmed.contains(msgKey)) return;
        SeasonOffer o = season(topic, p).orElse(null);
        if (o == null) {
            client.edit(chatId, messageId, actions.text("support.plan.seasonGone"), null);
            return;
        }
        if (!confirmed.add(msgKey)) return;
        if (confirmed.size() > 10_000) confirmed.clear();
        String error = actions.runAs(staff, me -> billing.recordPayment(topic.farmerId(), new PaymentRequest(p.id(), o.finalPrice(),
                null, null, method, o.from(), o.until(), "Mavsumiy obuna (−" + o.discountPct() + "%), Telegram support guruhidan",
                null, null)));
        if (error != null) {
            confirmed.remove(msgKey);                                // xato — tugmalar qoladi, qayta bosish mumkin
            client.edit(chatId, messageId, "⚠️ " + error + "\n\n" + confirmText(topic, p, SEASON, method, o.finalPrice()),
                    confirmButtons(p, SEASON, method));
            return;
        }
        client.edit(chatId, messageId, actions.text("support.plan.done", p.name(), HumanDates.format("uz", o.until()),
                staff.user().email()) + "\n💰 " + money(o.finalPrice(), p), null);
    }

    private Optional<SeasonOffer> season(SupportStore.Topic topic, PlanResponse p) {
        return seasons.offer(topic.farmerId(), LocalDate.now()).filter(o -> o.planId().equals(p.id()));
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    /** To'lov yozish huquqi (billing:manage) bor xodim; aks holda bo'sh — ro'yxat ham ko'rsatilmaydi */
    private Optional<SupportStore.Staff> billingStaff(long telegramUserId) {
        return store.staff(telegramUserId).filter(s -> s.user().permissions().contains(Permissions.BILLING_MANAGE));
    }

    /** Faqat faol pullik tariflar (callback_data ishonchsiz) */
    private Optional<PlanResponse> plan(long id) {
        return plans.catalog().stream().filter(p -> !p.systemPlan() && p.id() == id).findFirst();
    }

    private InlineKeyboardMarkup planButtons() {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        for (PlanResponse p : plans.catalog()) {
            if (p.systemPlan()) continue;
            String price = p.contactSales() ? actions.text("support.plan.contact")
                    : money(p.price(), p) + " / " + actions.text("support.plan.days", String.valueOf(p.durationDays()));
            kb.addRow(new InlineKeyboardButton((p.contactSales() ? "🏢 " : "💎 ") + p.name() + " — " + price).callbackData(PLAN + p.id()));
        }
        kb.addRow(new InlineKeyboardButton(actions.text("support.plan.cancel")).callbackData(CANCEL));
        return kb;
    }

    private InlineKeyboardMarkup periodButtons(PlanResponse p, SupportStore.Topic topic) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int n : PERIOD_OPTIONS) row.add(new InlineKeyboardButton(periodLabel(p, n)).callbackData(PERIODS + p.id() + ":" + n));
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(row.toArray(InlineKeyboardButton[]::new));
        season(topic, p).ifPresent(o -> kb.addRow(new InlineKeyboardButton(actions.text("support.plan.season",
                HumanDates.format("uz", o.until()), String.valueOf(o.discountPct()))).callbackData(PERIODS + p.id() + ":" + SEASON)));
        kb.addRow(new InlineKeyboardButton(actions.text("support.plan.cancel")).callbackData(CANCEL));
        return kb;
    }

    private InlineKeyboardMarkup methodButtons(PlanResponse p, int n) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (PaymentMethod m : METHODS) {
            row.add(new InlineKeyboardButton(actions.text("support.plan.method." + m.name()))
                    .callbackData(METHOD + p.id() + ":" + n + ":" + m.name()));
            if (row.size() == 2) {
                kb.addRow(row.toArray(InlineKeyboardButton[]::new));
                row.clear();
            }
        }
        kb.addRow(new InlineKeyboardButton(actions.text("support.plan.cancel")).callbackData(CANCEL));
        return kb;
    }

    private String confirmText(SupportStore.Topic topic, PlanResponse p, int n, PaymentMethod method, BigDecimal amount) {
        if (n == SEASON) {
            SeasonOffer o = season(topic, p).orElse(null);
            if (o == null) return actions.text("support.plan.seasonGone");
            return actions.text("support.plan.confirm", topic.farmerName(), p.name(), periodLabel(p, n),
                    HumanDates.format("uz", o.from()) + " — " + HumanDates.format("uz", o.until()),
                    money(amount, p), actions.text("support.plan.method." + method.name()))
                    + "\n🌱 " + actions.text("support.plan.seasonLine", money(o.basePrice(), p), String.valueOf(o.discountPct()));
        }
        SupportStore.Period period = store.period(topic.farmerId(), p.durationDays(), n);
        PlanPrice unit = p.contactSales() ? null : unitPrice(topic, p);
        String discount = unit != null && unit.discounted()
                ? "\n🏷 " + actions.text("support.plan.discount", unit.discountName(), unit.discountLabel()) : "";
        return actions.text("support.plan.confirm", topic.farmerName(), p.name(), periodLabel(p, n),
                HumanDates.format("uz", period.from()) + " — " + HumanDates.format("uz", period.until()),
                money(amount, p), actions.text("support.plan.method." + method.name())) + discount;
    }

    /** Fermerga mos eng foydali chegirma bilan bir davr narxi */
    private PlanPrice unitPrice(SupportStore.Topic topic, PlanResponse p) {
        return pricing.price(topic.farmerId(), p, LocalDate.now());
    }

    private InlineKeyboardMarkup confirmButtons(PlanResponse p, int n, PaymentMethod method) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton(actions.text("support.plan.ok")).callbackData(CONFIRM + p.id() + ":" + n + ":" + method.name()),
                new InlineKeyboardButton(actions.text("support.plan.cancel")).callbackData(CANCEL));
    }

    /** Tarif muddati ~30 kun — "3 oy"; boshqa muddat (masalan 365) — "3 × 365 kun" (yil 3 oy bo'lib ko'rinmasin) */
    private String periodLabel(PlanResponse p, int n) {
        if (n == SEASON) return actions.text("support.plan.seasonShort");
        int d = p.durationDays() == null ? 30 : p.durationDays();
        return d >= 28 && d <= 31 ? actions.text("support.plan.months", String.valueOf(n))
                : n + " × " + actions.text("support.plan.days", String.valueOf(d));
    }

    private static int periods(String s) {
        int n = Integer.parseInt(s);
        if (n == SEASON) return SEASON;
        for (int o : PERIOD_OPTIONS) if (o == n) return n;
        return 0;
    }

    private static PaymentMethod method(String s) {
        return METHODS.stream().filter(m -> m.name().equals(s)).findFirst().orElse(null);
    }

    /** "1 500 000", "1500000 so'm" → butun son; noto'g'ri yoki 0 — null */
    static BigDecimal parseAmount(String text) {
        String digits = text.replaceAll("(?i)\\s*(so['ʻ’]?m|сум|sum|uzs|usd|\\$)\\.?$", "").replaceAll("[\\s ,.]", "");
        if (!digits.matches("\\d{1,10}")) return null;
        BigDecimal v = new BigDecimal(digits);
        return v.signum() > 0 && v.compareTo(MAX_AMOUNT) <= 0 ? v : null;
    }

    private static String key(long thread, long staffTelegramId) {
        return thread + ":" + staffTelegramId;
    }

    private String currency(PlanResponse p) {
        return "UZS".equals(p.currency()) ? "soʻm" : p.currency();
    }

    private String money(BigDecimal v, PlanResponse p) {
        return Money.digits(v) + " " + currency(p);
    }
}
