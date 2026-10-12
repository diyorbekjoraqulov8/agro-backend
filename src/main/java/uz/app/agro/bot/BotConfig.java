package uz.app.agro.bot;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotConfig {

    @Bean
    public TelegramBot telegramBot(TelegramProperties props) {
        if (props.botToken() == null || props.botToken().isEmpty()) {
            throw new IllegalStateException("telegram.enabled=true, lekin TELEGRAM_BOT_TOKEN berilmagan");
        }
        return new TelegramBot(props.botToken());
    }
}
