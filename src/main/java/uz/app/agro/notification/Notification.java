package uz.app.agro.notification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yuboriladigan xabar (navbatga qo'yish so'rovi). Ikki xil:
 *   text(...)     — tayyor matn (+ havola tugmalari), har kanal o'zi chiqaradi;
 *   template(...) — kanal renderer'i formatlaydi (tahlil hisoboti, sug'orish eslatmasi…), params — kerakli id/qiymatlar.
 * Misol: Notification.template(farmerId, ANALYSIS, "analysis.completed").param("jobId", 12).preview("Tahlil №12 tayyor")
 */
public final class Notification {

    public static final String TEXT = "text";

    /** Havola tugmasi (matn + https url) — barcha kanallar uchun umumiy */
    public record Button(String text, String url) {}

    final long farmerId;
    final NotificationCategory category;
    final String template;
    final Map<String, Object> params = new HashMap<>();
    String preview;
    String refType;
    Long refId;
    Long campaignId;
    ChannelType channel = ChannelType.TELEGRAM;

    private Notification(long farmerId, NotificationCategory category, String template) {
        this.farmerId = farmerId;
        this.category = category;
        this.template = template;
    }

    public static Notification template(long farmerId, NotificationCategory category, String template) {
        return new Notification(farmerId, category, template);
    }

    public static Notification text(long farmerId, NotificationCategory category, String text, List<Button> buttons) {
        Notification n = new Notification(farmerId, category, TEXT);
        n.params.put("text", text);
        if (buttons != null && !buttons.isEmpty()) {
            n.params.put("buttons", buttons.stream().map(b -> Map.of("text", b.text(), "url", b.url())).toList());
        }
        n.preview = text;
        return n;
    }

    public Notification param(String key, Object value) {
        if (value != null) params.put(key, value);
        return this;
    }

    public Notification preview(String preview) {
        this.preview = preview;
        return this;
    }

    /** Yetkazilganda shu obyekt yangilanadi (NotificationDeliveredEvent) — masalan FEEDBACK_MESSAGE + id */
    public Notification ref(String type, long id) {
        this.refType = type;
        this.refId = id;
        return this;
    }

    Notification campaign(long campaignId) {
        this.campaignId = campaignId;
        return this;
    }
}
