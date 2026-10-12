package uz.app.agro.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.GetMe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Bot username (taklif havolasi t.me/<username>?start=...). getMe — bir marta, keyin keshdan */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotIdentity {

    private final TelegramBot bot;
    private final AtomicReference<String> username = new AtomicReference<>();

    /** null — Telegram javob bermadi (keyingi chaqiruvda qayta uriniladi) */
    public String username() {
        String u = username.get();
        if (u != null) return u;
        try {
            var r = bot.execute(new GetMe());
            if (r.isOk() && r.user() != null && r.user().username() != null) {
                username.compareAndSet(null, r.user().username());
                return username.get();
            }
            log.warn("telegram getMe {}: {}", r.errorCode(), r.description());
        } catch (RuntimeException e) {
            log.warn("telegram getMe: {}", e.getMessage());
        }
        return null;
    }
}
