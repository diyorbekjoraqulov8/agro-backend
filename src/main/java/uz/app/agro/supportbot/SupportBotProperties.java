package uz.app.agro.supportbot;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Alohida support bot (@BotFather) — xodimlar guruhida (Topics yoqilgan) har fermerga bitta mavzu.
 * token bo'sh — support bot o'chiq (eski TELEGRAM_SUPPORT_CHAT_ID xabarnomasi ishlayveradi).
 * @NotBlank emas: yechilmagan ${...} literal satr bo'lib keladi (CLAUDE.md 5.5) — shakl tekshiruvi
 */
@Validated
@ConfigurationProperties(prefix = "support-bot")
public record SupportBotProperties(
        @Pattern(regexp = "|\\d+:[A-Za-z0-9_-]{30,}", message = "support-bot.token shakli: 123456:ABC... (SUPPORT_BOT_TOKEN)")
        String token,

        // Xodimlar guruhi (-100...). Bo'sh — bot faqat /id ga javob beradi (guruh id'sini bilish uchun)
        Long groupId,

        // false — getUpdates qilmaydi (2-instans, sinov); faqat guruhga yozadi
        boolean polling,

        // Admin panel manzili (https) — mavzudagi "🔗 Admin'da ochish" tugmasi. Bo'sh — tugma yo'q
        @Pattern(regexp = "|https://[^\\s/]+(/[^\\s]*)?", message = "support-bot.admin-url https:// bilan boshlansin (ADMIN_URL)")
        String adminUrl
) {
    public boolean groupConfigured() {
        return groupId != null && groupId != 0;
    }
}
