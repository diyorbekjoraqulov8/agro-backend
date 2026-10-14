package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.analysis.AnalysisReportService;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.irrigation.FieldWaterEntity;
import uz.app.agro.irrigation.WaterStatus;
import uz.app.agro.notification.ChannelType;
import uz.app.agro.notification.Templates;
import uz.app.agro.notification.NotificationChannel;

import java.util.List;
import java.util.Map;

/**
 * Telegram kanali: outbox'dagi xabarni shablon bo'yicha chiqaradi. Mavjud formatlovchilar qayta ishlatiladi
 * (hisobot — BotReportSender, sug'orish — BotIrrigation). Dispatcher thread'ida, tranzaksiyasiz.
 * Yuborilmadi: fermer bloklagan (403) — FAILED (qayta urinish befoyda), aks holda — RETRY (tarmoq, 429).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotNotificationChannel implements NotificationChannel {

    private final BotSender sender;
    private final BotMessages messages;
    private final BotKeyboards keyboards;
    private final BotStateService states;
    private final BotUpdateHandler handler;
    private final BotReportSender reports;
    private final BotReportFormatter format;
    private final BotIrrigation irrigation;
    private final AnalysisReportService analysisReports;
    private final FileRegistry files;
    private final FarmerRegistrationService farmers;
    private final BotWeather weather;
    private final BotMonitoring monitoring;
    private final BotAdvisor advisor;

    @Override
    public ChannelType type() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public Delivery deliver(Recipient to, String template, Map<String, Object> p) {
        MDC.put("userId", "tg:" + to.telegramUserId());      // tozalash — NotificationDispatcher finally
        if (Templates.TEXT.equals(template)) return text(to, p);
        boolean ok = switch (template) {
            case Templates.ANALYSIS_COMPLETED -> analysisCompleted(to, p);
            case Templates.ANALYSIS_REJECTED -> analysisRejected(to, p);
            case Templates.ANALYSIS_FAILED -> sender.send(to.chatId(), format.technical(to.language()), null) != null
                    && moveTo(to, BotStep.REPORTED);
            case Templates.IRRIGATION_REMINDER -> irrigation.remind(to, JsonMapper.shared().convertValue(p.get("status"), WaterStatus.class),
                    FieldWaterEntity.ReminderKind.valueOf(String.valueOf(p.get("kind"))));
            case Templates.FEEDBACK_REPLY -> feedbackReply(to, p);
            case Templates.FEEDBACK_CLOSED -> feedbackClosed(to, p);
            case Templates.WEATHER_ALERT -> weather.alert(to, p);
            case Templates.MONITORING_ALERT -> monitoring.alert(to, p);
            case Templates.ADVISOR_ANSWER -> advisor.answer(to, p);
            default -> {
                log.error("noma'lum bildirishnoma shabloni: {}", template);
                yield false;
            }
        };
        if (ok) return Delivery.sent();
        if (!isKnown(template)) return Delivery.failed("noma'lum shablon: " + template);
        return farmers.isBlocked(to.chatId()) ? Delivery.failed("fermer botni bloklagan yoki chat yoʻq") : Delivery.retry("telegram yubormadi");
    }

    /** Kampaniya/matn — Telegram rad etsa sababi jurnalga (masalan "BUTTON_URL_INVALID") */
    private Delivery text(Recipient to, Map<String, Object> p) {
        InlineKeyboardMarkup kb = null;
        if (p.get("buttons") instanceof List<?> buttons && !buttons.isEmpty()) {
            kb = new InlineKeyboardMarkup();
            for (Object b : buttons) {
                if (b instanceof Map<?, ?> m) kb.addRow(new InlineKeyboardButton(String.valueOf(m.get("text"))).url(String.valueOf(m.get("url"))));
            }
        }
        BotSender.Sent s = sender.sendDetailed(to.chatId(), String.valueOf(p.get("text")), kb);
        if (s.messageId() != null) return Delivery.sent();
        String reason = "telegram" + (s.errorCode() == null ? "" : " " + s.errorCode()) + ": " + s.error();
        return s.permanent() || farmers.isBlocked(to.chatId()) ? Delivery.failed(reason) : Delivery.retry(reason);
    }

    private boolean analysisCompleted(Recipient to, Map<String, Object> p) {
        long jobId = num(p, "jobId");
        var report = analysisReports.forFarmer(to.telegramUserId(), jobId).orElse(null);
        if (report == null) return true;                 // dala o'chirilgan — yuboradigan narsa yo'q (qayta urinish befoyda)
        if (!reports.send(to.chatId(), to.language(), report.input(), report.result())) return false;
        moveTo(to, BotStep.REPORTED);
        // Sug'orish eslatmasi hali sozlanmagan — tuproq va oxirgi sug'orish savollari
        irrigation.promptIfNeeded(to.telegramUserId(), to.chatId(), to.language(), report.input().fieldId());
        return true;
    }

    private boolean analysisRejected(Recipient to, Map<String, Object> p) {
        if (sender.send(to.chatId(), format.rejected(to.language(), String.valueOf(p.get("code"))), null) == null) return false;
        // Qayta chizsin: FAILED job kvotani yemaydi; menyuda "🗺 Yangi dala" bor
        moveTo(to, BotStep.WAIT_POLYGON);
        handler.showMenu(to.chatId(), to.language());
        return true;
    }

    /** Operator xabari: matn, fayl yoki ikkalasi; tugmalar — "✍️ Javob yozish / ✅ Yakunlash" */
    private boolean feedbackReply(Recipient to, Map<String, Object> p) {
        long feedbackId = num(p, "feedbackId");
        String text = p.get("text") == null ? null : String.valueOf(p.get("text"));
        String lang = to.language();
        String id = String.valueOf(feedbackId);           // MessageFormat 1234 ni "1 234" qilmasin
        String head = messages.text(lang, "feedback.reply.file", id);
        String full = text == null ? head : messages.text(lang, "feedback.reply", id, text);
        var kb = keyboards.staffMessage(lang, feedbackId);
        if (p.get("fileHashId") == null) return sender.send(to.chatId(), full, kb) != null;
        FileRegistry.Content c = files.content(String.valueOf(p.get("fileHashId")));
        var f = c.info();
        if (full.length() <= 1024) return sender.sendFile(to.chatId(), c.bytes(), f.name(), f.contentType(), f.image(), full, kb);
        // Uzun matn: avval fayl (qisqa sarlavha), keyin matn — tugmalar oxirgi xabarda
        return sender.sendFile(to.chatId(), c.bytes(), f.name(), f.contentType(), f.image(), head, null)
                && sender.send(to.chatId(), full, kb) != null;
    }

    private boolean feedbackClosed(Recipient to, Map<String, Object> p) {
        long feedbackId = num(p, "feedbackId");
        // Fermer shu suhbat rejimida bo'lsa — asosiy menyu klaviaturasi qaytadi
        boolean wasInChat = states.leaveChatIf(to.telegramUserId(), feedbackId);
        boolean ok = sender.send(to.chatId(), messages.text(to.language(), "feedback.closed.staff", String.valueOf(feedbackId)),
                wasInChat ? handler.menu(to.chatId(), to.language()) : null) != null;
        if (ok && Boolean.TRUE.equals(p.get("askRating"))) handler.askRating(to.chatId(), to.language(), feedbackId);
        return ok;
    }

    private boolean moveTo(Recipient to, BotStep step) {
        states.moveTo(to.telegramUserId(), step);
        return true;
    }

    private static long num(Map<String, Object> p, String key) {
        return ((Number) p.get(key)).longValue();
    }

    private static boolean isKnown(String template) {
        return switch (template) {
            case Templates.TEXT, Templates.ANALYSIS_COMPLETED, Templates.ANALYSIS_REJECTED, Templates.ANALYSIS_FAILED, Templates.IRRIGATION_REMINDER,
                 Templates.FEEDBACK_REPLY, Templates.FEEDBACK_CLOSED, Templates.WEATHER_ALERT,
                 Templates.MONITORING_ALERT, Templates.ADVISOR_ANSWER -> true;
            default -> false;
        };
    }
}
