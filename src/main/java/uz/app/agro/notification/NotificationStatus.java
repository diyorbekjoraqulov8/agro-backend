package uz.app.agro.notification;

/** PENDING → SENDING → SENT | (qayta PENDING) | FAILED. SKIPPED — fermer botni bloklagan; CANCELLED — kampaniya bekor */
public enum NotificationStatus { PENDING, SENDING, SENT, FAILED, SKIPPED, CANCELLED }
