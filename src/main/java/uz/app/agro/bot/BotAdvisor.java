package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.advisor.AdvisorBotService;
import uz.app.agro.advisor.AdvisorDiagnosis;
import uz.app.agro.advisor.Texts;
import uz.app.agro.field.FieldBotService;
import uz.app.agro.field.FieldBotService.FieldCard;
import uz.app.agro.notification.NotificationChannel.Recipient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bo'lim "🤖 AI maslahat": dala tanlash (bir nechta bo'lsa) → rasm (ADVISOR_PHOTO rejimi) → navbat → AdvisorJob →
 * javob bildirishnoma orqali (Templates.ADVISOR_ANSWER — BotNotificationChannel chaqiradi). Agronom tekshiruvi yo'q:
 * tavsiya yakuniy, "agronom bilan maslahatlashing" ogohlantirishi bilan.
 * Rasmni yuklab saqlash — BotUpdateHandler (fayl qabul qilish o'sha yerda).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotAdvisor {

    private final BotSender sender;
    private final BotMessages messages;
    private final BotKeyboards keyboards;
    private final BotStateService states;
    private final FieldBotService fields;
    private final AdvisorBotService advisor;
    private final BotPlans planSection;

    /** Menyu tugmasi: limitni tekshirib, dala tanlash yoki to'g'ridan-to'g'ri rasm so'rash */
    public void start(long tg, long chatId, String lang) {
        if (!allowed(tg, chatId, lang)) return;
        List<FieldCard> list = fields.list(tg);
        if (list.size() > 1) {
            sender.send(chatId, messages.text(lang, "advisor.chooseField"), keyboards.advisorFields(lang, list));
            return;
        }
        askPhoto(tg, chatId, lang, list.isEmpty() ? 0 : list.getFirst().id());
    }

    /** "av:<fieldId>" — @return true: bu bo'lim tugmasi edi */
    public boolean onCallback(String callbackId, long tg, long chatId, String lang, String data) {
        if (!data.startsWith(BotKeyboards.ADVISOR)) return false;
        sender.answer(callbackId, null);
        long fieldId;
        try {
            fieldId = Long.parseLong(data.substring(BotKeyboards.ADVISOR.length()));
        } catch (NumberFormatException e) {
            return true;
        }
        // Egalik submit'da tekshiriladi (begona id — dalasiz bo'lib ketadi)
        if (allowed(tg, chatId, lang)) askPhoto(tg, chatId, lang, fieldId);
        return true;
    }

    /**
     * Rasm saqlandi — navbatga, rejimdan chiqiladi. @return so'rov id; bo'sh — shu orada limit tugagan / o'chirilgan.
     * Fermerga javobni chaqiruvchi yuboradi — asosiy menyu klaviaturasi bilan (rejimdagi "Bekor qilish" qolmasin)
     */
    public Optional<Long> submit(long tg, long chatId, String lang, String hashId, String note) {
        Long field = states.advisorField(tg).orElse(null);
        states.leaveFeedback(tg);
        return advisor.submit(tg, field, hashId, note);
    }

    // ── Bildirishnomalar ──────────────────────────────────────────────────

    /** AI tavsiyasi (yoki AI javob bermadi — qayta yuborsin, limitdan ayirilmadi). @return true: yuborildi / yuboradigan narsa yo'q */
    public boolean answer(Recipient to, Map<String, Object> p) {
        var v = advisor.view(to.telegramUserId(), id(p)).orElse(null);
        if (v == null) return true;
        String lang = to.language();
        String text = v.diagnosis() == null
                ? messages.text(lang, "advisor.failed", String.valueOf(v.id()))
                : render(lang, v.id(), v.diagnosis());
        return sender.send(to.chatId(), text, null) != null;
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    private void askPhoto(long tg, long chatId, String lang, long fieldId) {
        states.enterAdvisor(tg, fieldId);
        sender.send(chatId, messages.text(lang, "advisor.askPhoto"), keyboards.cancelOnly(lang));
    }

    /** Rasm yuborilgan paytda limit tugagan (kamdan-kam: shu orada sozlama o'zgardi) — sababi */
    public void explainBlocked(long tg, long chatId, String lang) {
        allowed(tg, chatId, lang);
    }

    /** @return false — fermerga sababi aytildi (o'chiq / bepul sinov ishlatilgan / kunlik limit) */
    private boolean allowed(long tg, long chatId, String lang) {
        var q = advisor.quota(tg);
        switch (q.access()) {
            case ALLOWED -> { return true; }
            case DISABLED -> sender.send(chatId, messages.text(lang, "advisor.disabled"), null);
            case FREE_USED -> {
                // Tarif tugmalari: "📋 Barcha tariflar" + eng arzon pullik
                var kb = planSection.planButtons(lang, false);
                sender.send(chatId, messages.text(lang, "advisor.freeUsed", String.valueOf(q.limit())), kb);
            }
            case DAILY_LIMIT -> sender.send(chatId, messages.text(lang, "advisor.dailyLimit", String.valueOf(q.limit())), null);
        }
        return false;
    }

    private String render(String lang, long id, AdvisorDiagnosis d) {
        List<String> parts = new ArrayList<>();
        parts.add(messages.text(lang, "advisor.answer.title", String.valueOf(id)) + "\n\n" + d.summary());
        if (d.category() == AdvisorDiagnosis.Category.UNCLEAR || d.category() == AdvisorDiagnosis.Category.NOT_PLANT) {
            if (d.photoAdvice() != null && !d.photoAdvice().isBlank()) parts.add("📸 " + d.photoAdvice());
            parts.add(messages.text(lang, "advisor.answer.retry"));
            return String.join("\n\n", parts);
        }
        if (d.urgent()) parts.add(messages.text(lang, "advisor.answer.urgent"));
        if (d.causes() != null && !d.causes().isEmpty()) {
            StringBuilder b = new StringBuilder(messages.text(lang, "advisor.answer.causes"));
            for (var c : d.causes()) {
                b.append("\n• ").append(c.name()).append(" — ").append(c.confidencePct()).append("%");
                if (c.evidence() != null && !c.evidence().isBlank()) b.append("\n   ").append(c.evidence());
            }
            parts.add(b.toString());
        }
        parts.add(list(messages.text(lang, "advisor.answer.checks"), d.checks()));
        parts.add(list(messages.text(lang, "advisor.answer.actions"), d.actions()));
        parts.add(messages.text(lang, "advisor.answer.disclaimer"));
        parts.removeIf(String::isEmpty);
        return Texts.cut(String.join("\n\n", parts), 4096);
    }

    private static String list(String title, List<String> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder b = new StringBuilder(title);
        for (String s : items) b.append("\n• ").append(s);
        return b.toString();
    }

    private static long id(Map<String, Object> p) {
        return ((Number) p.get("requestId")).longValue();
    }
}
