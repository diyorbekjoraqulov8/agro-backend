package uz.app.agro.feedback.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Matn yoki fayl (yoki ikkalasi). Fayl avval POST /files bilan yuklanadi, bu yerga uning hashId'si.
 * Telegram xabari ≤ 4096 belgi; sarlavha ("💬 Mutaxassis javobi · №…") uchun joy qoldiramiz.
 */
public record ReplyRequest(
        @Size(max = 3500, message = "text ko'pi bilan 3500 belgi")
        String text,
        @Pattern(regexp = "[A-Za-z0-9_-]{22}", message = "fileHashId noto'g'ri")
        String fileHashId
) {}
