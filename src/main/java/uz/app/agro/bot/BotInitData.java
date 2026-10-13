package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Telegram Mini App initData imzosi (core.telegram.org/bots/webapps — "Validating data received via the Mini App"):
 *   secret = HMAC_SHA256(key="WebAppData", msg=bot_token);
 *   hash   = hex(HMAC_SHA256(key=secret, msg=data_check_string)) — hash'dan boshqa barcha maydonlar, alifbo tartibida, "k=v\n".
 * Imzo — bot tokeni bilan: uni faqat Telegram yasay oladi. auth_date eski bo'lsa (o'g'irlangan initData) — rad.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotInitData {

    static final Duration MAX_AGE = Duration.ofHours(24);
    private static final int MAX_LENGTH = 8192;

    private final TelegramProperties props;

    /** @return Telegram foydalanuvchi id'si — imzo to'g'ri va yangi bo'lsa */
    public Optional<Long> verify(String initData) {
        if (initData == null || initData.isBlank() || initData.length() > MAX_LENGTH) return Optional.empty();
        Map<String, String> fields = new TreeMap<>();
        String hash = null;
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) return Optional.empty();
            String k, v;
            try {
                k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {      // "%zz", oxirida yolg'iz "%" — mijoz kirishi, 500 emas
                return Optional.empty();
            }
            if ("hash".equals(k)) hash = v;
            else fields.put(k, v);
        }
        if (hash == null || !hash.matches("[0-9a-f]{64}")) return Optional.empty();
        StringBuilder check = new StringBuilder();
        fields.forEach((k, v) -> check.append(check.isEmpty() ? "" : "\n").append(k).append('=').append(v));
        byte[] expected = hmac(hmac("WebAppData".getBytes(StandardCharsets.UTF_8), props.botToken()), check.toString());
        // Doimiy vaqtli taqqoslash (CLAUDE.md 5.6) — imzoni bayt-bayt topib bo'lmasin
        if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(hash))) return Optional.empty();
        try {
            long authDate = Long.parseLong(fields.getOrDefault("auth_date", "0"));
            if (Instant.ofEpochSecond(authDate).isBefore(Instant.now().minus(MAX_AGE))) return Optional.empty();
            JsonNode user = JsonMapper.shared().readTree(fields.getOrDefault("user", "{}"));
            return user.hasNonNull("id") ? Optional.of(user.get("id").asLong()) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
