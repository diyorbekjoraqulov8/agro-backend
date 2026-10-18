package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Pastki menyu (reply keyboard) tugmasidan ochilgan Mini App'da initData BO'SH — server bilan gaplashish uchun bot URL'ga
 * imzolangan token qo'shadi: "tg.exp.sig" (sig = HMAC-SHA256, kalit bot tokenidan olingan). Faqat yengil, cheklangan
 * amallar uchun (dala chegarasini aniqlash) — dala saqlash baribir sendData/initData orqali.
 * Klaviatura telefonda haftalab turadi — muddat 30 kun; menyu qayta yuborilganda yangilanadi.
 * ⚠️ Faqat 🪄 uchun (ma'lumotga kirish bermaydi): token URL'da — boshqa endpoint'ga ULANMASIN.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
class MiniAppToken {

    static final Duration TTL = Duration.ofDays(30);

    private final TelegramProperties props;
    private volatile byte[] derived;                     // bot tokenidan olingan kalit — bir marta hisoblanadi

    String sign(long telegramUserId) {
        long exp = Instant.now().plus(TTL).getEpochSecond();
        String payload = telegramUserId + "." + exp;
        return payload + "." + mac(payload);
    }

    /** @return telegram user id — imzo to'g'ri va muddati o'tmagan */
    Optional<Long> verify(String token) {
        if (token == null || token.length() > 200) return Optional.empty();
        String[] p = token.split("\\.");
        if (p.length != 3) return Optional.empty();
        String expected = mac(p[0] + "." + p[1]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), p[2].getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        try {
            if (Instant.ofEpochSecond(Long.parseLong(p[1])).isBefore(Instant.now())) return Optional.empty();
            return Optional.of(Long.parseLong(p[0]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String mac(String payload) {
        try {
            byte[] k = derived;
            if (k == null) {
                Mac key = Mac.getInstance("HmacSHA256");
                key.init(new SecretKeySpec("DropyMiniAppToken".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                derived = k = key.doFinal(props.botToken().getBytes(StandardCharsets.UTF_8));   // initData kalitidan boshqa
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(k, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
