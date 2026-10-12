package uz.app.agro.bot;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        boolean enabled,

        // Bo'sh — faqat enabled=false da ruxsat (BotConfig tekshiradi).
        // @NotBlank emas: yechilmagan ${TELEGRAM_BOT_TOKEN} literal satr bo'lib keladi (CLAUDE.md 5.5)
        @Pattern(regexp = "|\\d+:[A-Za-z0-9_-]{30,}", message = "telegram.bot-token shakli: 123456:ABC... (TELEGRAM_BOT_TOKEN)")
        String botToken,

        // S6: Mini App (poligon chizish). https majburiy — Telegram talabi
        @Pattern(regexp = "|https://.+", message = "telegram.miniapp-url https:// bilan boshlansin (MINIAPP_URL)")
        String miniappUrl,

        // Xodimlar guruhi/kanali — yangi murojaat shu yerga ham keladi. Bo'sh — yuborilmaydi.
        // Guruh id'si manfiy (-100...): botni guruhga qo'shing, keyin getUpdates'dan oling
        Long supportChatId,

        // Bizning API'ning ommaviy manzili (https) — inline tugmadan ochilgan Mini App dalani shu yerga yuboradi
        // (sendData faqat reply-klaviaturadan ishlaydi). Bo'sh — inline tugma yo'q, faqat pastki menyu tugmasi
        @Pattern(regexp = "|https://[^\\s/]+(/[^\\s]*)?", message = "telegram.public-api-url https:// bilan boshlansin (PUBLIC_API_URL)")
        String publicApiUrl
) {
    public boolean inlineMapEnabled() {
        return publicApiUrl != null && !publicApiUrl.isBlank() && miniappUrl != null && !miniappUrl.isBlank();
    }
}
