package uz.app.agro.notification;

import java.util.Map;

/**
 * Yetkazish kanali (Telegram, keyin mobil push, web). Dispatcher thread'ida, tranzaksiyasiz chaqiriladi (tarmoq).
 * Yangi kanal — shu interfeys implementatsiyasi; xabar yuboradigan kod o'zgarmaydi.
 */
public interface NotificationChannel {

    ChannelType type();

    Delivery deliver(Recipient to, String template, Map<String, Object> params);

    /** Kimga: kanal o'zi kerakli manzilni oladi (Telegram — chatId) */
    record Recipient(long farmerId, long telegramUserId, long chatId, String language, boolean blocked) {}

    /** SENT — yetdi; RETRY — vaqtinchalik xato (tarmoq, limit); FAILED — qayta urinish befoyda (bloklagan, noma'lum shablon) */
    record Delivery(Result result, String error) {
        public enum Result { SENT, RETRY, FAILED }
        public static Delivery sent() { return new Delivery(Result.SENT, null); }
        public static Delivery retry(String error) { return new Delivery(Result.RETRY, error); }
        public static Delivery failed(String error) { return new Delivery(Result.FAILED, error); }
    }
}
