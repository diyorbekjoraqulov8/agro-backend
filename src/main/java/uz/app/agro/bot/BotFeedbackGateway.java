package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.feedback.FeedbackGateway;

import java.util.Optional;

/** Murojaatlar → Telegram fayllari (eski rasmlarni serverga ko'chirish). Xabarlar — BotNotificationChannel */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotFeedbackGateway implements FeedbackGateway {

    private final BotFiles files;

    @Override
    public Optional<byte[]> download(String telegramFileId) {
        return files.download(telegramFileId);
    }
}
