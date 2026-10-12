package uz.app.agro.feedback;

import java.util.Optional;

/**
 * Telegram'dagi fayl (eski xabarlardagi photo_file_id) — serverga ko'chirish uchun (FeedbackFileBackfill).
 * Implementatsiya — bot paketida (telegram.enabled=true). Fermerga xabar — NotificationService orqali.
 */
public interface FeedbackGateway {

    /** Bo'sh — topilmadi/eskirgan */
    Optional<byte[]> download(String telegramFileId);
}
