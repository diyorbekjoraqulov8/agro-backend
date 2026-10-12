package uz.app.agro.notification;

/** Xabar yakuniy holatga keldi (SENT/FAILED/SKIPPED) — ref egasi (masalan chat xabari) "yetkazildi" belgisini qo'yadi */
public record NotificationDeliveredEvent(long notificationId, String refType, Long refId, boolean delivered) {}
