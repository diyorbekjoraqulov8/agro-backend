package uz.app.agro.feedback.dto;

import uz.app.agro.feedback.FeedbackSender;
import uz.app.agro.file.dto.FileResponse;

import java.time.LocalDateTime;

/**
 * @param file         biriktirilgan fayl (rasm/PDF) — to'liq ma'lumoti bilan; kontent — file.url
 * @param photoPending eski Telegram rasmi hali serverga ko'chirilmagan (bot o'chiq yoki yuklab bo'lmadi)
 */
public record MessageResponse(
        Long id, FeedbackSender sender, Long staffId, String staffEmail, String text, FileResponse file,
        boolean photoPending, Boolean delivered, LocalDateTime createdDate
) {}
