package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.monitoring.MonitoringBotService;
import uz.app.agro.monitoring.Verdict;
import uz.app.agro.notification.NotificationChannel.Recipient;

import java.time.LocalDate;
import java.util.Map;

/**
 * C3: monitoring ogohlantirishi (Templates.MONITORING_ALERT — BotNotificationChannel chaqiradi) — o'zgarish xaritasi
 * rasm sifatida, ostida "🔄 Qayta tahlil" va "📋 Dala". Dala kartochkasidagi holat qatori.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotMonitoring {


    private final BotSender sender;
    private final BotMessages messages;
    private final FileRegistry files;
    private final MonitoringBotService monitoring;

    /** {fieldId, fieldName, sceneDate, refDate, declinePct, side, fileHashId?} — @return true: yuborildi */
    public boolean alert(Recipient to, Map<String, Object> p) {
        String lang = to.language();
        long fieldId = ((Number) p.get("fieldId")).longValue();
        String side = p.get("side") == null ? messages.text(lang, "side.CENTER") : messages.text(lang, "side." + p.get("side"));
        String text = messages.text(lang, "monitoring.alert", String.valueOf(p.get("fieldName")),
                day(lang, p.get("sceneDate")), String.valueOf(Math.round(((Number) p.get("declinePct")).doubleValue())), side, day(lang, p.get("refDate")));
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(
                new InlineKeyboardButton(messages.text(lang, "btn.reanalyze")).callbackData(BotKeyboards.FIELD_REANALYZE + fieldId),
                new InlineKeyboardButton(messages.text(lang, "monitoring.openField")).callbackData(BotKeyboards.FIELD_VIEW + fieldId));
        if (p.get("fileHashId") instanceof String hashId) {
            try {
                // Rasm izohi ≤ 1024 belgi — matn qisqa, legend bilan sig'adi
                return sender.sendPhoto(to.chatId(), files.content(hashId).bytes(),
                        text + "\n\n" + messages.text(lang, "monitoring.legend"), kb);
            } catch (RuntimeException e) {
                log.warn("monitoring xaritasi o'qilmadi ({}): {} — matn yuboriladi", hashId, e.toString());
            }
        }
        return sender.send(to.chatId(), text, kb) != null;
    }

    /**
     * Dala kartasi: "📊 Hozirgi holat" — monitoring yozgan oxirgi surat (darhol, Python'siz), 2 haftalik trend va
     * ogohlantirish holati. Monitoring o'chiq — bo'sh.
     */
    public String cardLine(String lang, long tg, long fieldId) {
        return monitoring.card(tg, fieldId).filter(MonitoringBotService.CardStatus::enabled).map(s -> {
            StringBuilder b = new StringBuilder();
            if (s.sceneDate() == null) {
                b.append(messages.text(lang, "field.status.pending"));
            } else {
                b.append(messages.text(lang, "field.status.title", BotMessages.date(lang, s.sceneDate())))
                        .append("\n").append(messages.text(lang, "field.status." + level(s)));
                if (s.verdict() == Verdict.DECLINE && s.declinePct() != null) {
                    b.append("\n").append(messages.text(lang, "field.status.decline", String.valueOf(Math.round(s.declinePct().doubleValue()))));
                }
                if (s.trendPct() != null) {
                    int t = s.trendPct();
                    b.append("\n").append(Math.abs(t) < 5 ? messages.text(lang, "field.status.trendFlat")
                            : messages.text(lang, t > 0 ? "field.status.trendUp" : "field.status.trendDown", String.valueOf(Math.abs(t))));
                }
            }
            b.append("\n").append(messages.text(lang, s.alerts() ? "field.status.alertsOn" : "field.status.alertsPro"));
            return b.toString();
        }).orElse("");
    }

    /** Yashillik darajasi: o'rim/shudgor — BARE; aks holda NDVI (dala medianasi) bo'yicha */
    private static String level(MonitoringBotService.CardStatus s) {
        if (s.verdict() == Verdict.HARVEST || s.verdict() == Verdict.NO_CROP) return "BARE";
        double v = s.ndvi() == null ? 0 : s.ndvi().doubleValue();
        return v >= 0.55 ? "GOOD" : v >= 0.35 ? "MID" : v >= 0.2 ? "LOW" : "BARE";
    }

    private static String day(String lang, Object isoDate) {
        return isoDate == null ? "—" : BotMessages.date(lang, LocalDate.parse(String.valueOf(isoDate)));
    }
}
