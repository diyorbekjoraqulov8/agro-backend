package uz.app.agro.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.GetFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/** Telegram serveridagi fayl (file_id) → bayt. getFile — 20 MB gacha. Tranzaksiyasiz chaqirilsin (tarmoq) */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotFiles {

    private final TelegramBot bot;

    public Optional<byte[]> download(String fileId) {
        try {
            var r = bot.execute(new GetFile(fileId));
            if (!r.isOk() || r.file() == null) {
                log.warn("telegram getFile {}: {}", r.errorCode(), r.description());
                return Optional.empty();
            }
            return Optional.ofNullable(bot.getFileContent(r.file()));
        } catch (IOException | RuntimeException e) {
            log.warn("telegram fayl yuklanmadi: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
