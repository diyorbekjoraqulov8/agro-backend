package uz.app.agro.bot;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.monitoring.MonitoringBotService;
import uz.app.agro.notification.NotificationChannel.Recipient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");

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
                day(p.get("sceneDate")), String.valueOf(Math.round(((Number) p.get("declinePct")).doubleValue())), side, day(p.get("refDate")));
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

    /** Dala kartochkasi: "🛰 Monitoring: oxirgi surat 10.10 — jiddiy o'zgarish yo'q". Bo'sh — ko'rsatilmaydi */
    public String cardLine(String lang, long tg, long fieldId) {
        return monitoring.card(tg, fieldId).map(s -> switch (s.mode()) {
            case OFF -> "";
            case PRO_ONLY -> messages.text(lang, "field.monitor.pro");
            case ON -> s.sceneDate() == null ? messages.text(lang, "field.monitor.pending")
                    : messages.text(lang, "field.monitor.on", s.sceneDate().format(DAY),
                            messages.text(lang, "monitor.verdict." + s.verdict(),
                                    s.declinePct() == null ? "0" : String.valueOf(Math.round(s.declinePct().doubleValue()))));
        }).orElse("");
    }

    private static String day(Object isoDate) {
        return isoDate == null ? "—" : LocalDate.parse(String.valueOf(isoDate)).format(DAY);
    }
}
