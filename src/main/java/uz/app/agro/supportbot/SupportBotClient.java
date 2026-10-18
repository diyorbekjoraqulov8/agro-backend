package uz.app.agro.supportbot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.model.reaction.ReactionTypeEmoji;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.CreateForumTopic;
import com.pengrad.telegrambot.request.EditForumTopic;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.ReopenForumTopic;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SetMessageReaction;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.CreateForumTopicResponse;
import com.pengrad.telegrambot.response.SendResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Support botning Telegram mijozi — asosiy botdan ALOHIDA token (o'z getUpdates oqimi). Spring bean emas TelegramBot:
 * asosiy bot bean'i bilan bir xil tur — inject qilinganda to'qnashmasin. Xato tashlamaydi — natija qaytaradi.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank()")
public class SupportBotClient {

    /** ok=false — description Telegram xatosi ("message thread not found" va h.k.) */
    public record Result(boolean ok, Integer messageId, String error) {}

    private final TelegramBot bot;

    public SupportBotClient(SupportBotProperties props) {
        this.bot = new TelegramBot(props.token());
    }

    TelegramBot raw() {
        return bot;
    }

    private volatile String username;

    /** Bot @username (bog'lash havolasi uchun) — bir marta getMe */
    public Optional<String> username() {
        if (username == null) {
            var r = execute(new GetMe());
            if (r != null && r.isOk() && r.user() != null) username = r.user().username();
        }
        return Optional.ofNullable(username);
    }

    public Result send(long chatId, Long threadId, String text, Keyboard keyboard) {
        SendMessage req = new SendMessage(chatId, cut(text, 4096));
        if (threadId != null) req.messageThreadId(threadId);
        if (keyboard != null) req.replyMarkup(keyboard);
        return result(execute(req));
    }

    /** Tugmali qadamlar (tarif berish) — o'sha xabarning o'zi almashadi, mavzu to'lib ketmasin */
    public void edit(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText req = new EditMessageText(chatId, messageId, cut(text, 4096));
        if (keyboard != null) req.replyMarkup(keyboard);
        execute(req);
    }

    public Result sendPhoto(long chatId, Long threadId, byte[] png, String caption) {
        SendPhoto req = new SendPhoto(chatId, png);
        if (threadId != null) req.messageThreadId(threadId);
        if (caption != null) req.caption(cut(caption, 1024));
        return result(execute(req));
    }

    public Result sendDocument(long chatId, Long threadId, byte[] content, String fileName, String contentType, String caption) {
        SendDocument req = new SendDocument(chatId, content).fileName(fileName).contentType(contentType);
        if (threadId != null) req.messageThreadId(threadId);
        if (caption != null) req.caption(cut(caption, 1024));
        return result(execute(req));
    }

    /** @return yangi mavzu (message_thread_id) yoki bo'sh — huquq yo'q / Topics yoqilmagan */
    public Optional<Long> createTopic(long chatId, String name) {
        BaseResponse r = execute(new CreateForumTopic(chatId, cut(name, 128)));
        if (r instanceof CreateForumTopicResponse t && t.isOk() && t.forumTopic() != null) {
            return Optional.ofNullable(t.forumTopic().messageThreadId());
        }
        return Optional.empty();
    }

    public void renameTopic(long chatId, long threadId, String name) {
        execute(new EditForumTopic(chatId, threadId).name(cut(name, 128)));
    }

    /** Xodim Telegram'da mavzuni yopib qo'ygan — yangi xabar uchun qayta ochamiz */
    public void reopenTopic(long chatId, long threadId) {
        execute(new ReopenForumTopic(chatId, threadId));
    }

    public void react(long chatId, int messageId, String emoji) {
        execute(new SetMessageReaction(chatId, messageId, new ReactionTypeEmoji(emoji)));
    }

    public void answer(String callbackId, String toast) {
        AnswerCallbackQuery req = new AnswerCallbackQuery(callbackId);
        if (toast != null) req.text(toast);
        execute(req);
    }

    /** Xodim guruhga yuborgan rasm → bayt (getFile — 20 MB gacha) */
    public Optional<byte[]> download(String fileId) {
        try {
            var r = bot.execute(new GetFile(fileId));
            if (!r.isOk() || r.file() == null) return Optional.empty();
            return Optional.ofNullable(bot.getFileContent(r.file()));
        } catch (IOException | RuntimeException e) {
            // e.getMessage() — fayl URL'i, ichida TOKEN: faqat exception turi log'ga
            log.warn("support bot: fayl yuklanmadi ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @PreDestroy
    void shutdown() {
        bot.shutdown();
    }

    private <T extends BaseRequest<T, R>, R extends BaseResponse> R execute(BaseRequest<T, R> req) {
        try {
            R r = bot.execute(req);
            // 429: guruhga daqiqasiga ~20 xabar — aytilgan vaqtni kutib bir marta qayta (bridge bitta thread — tartib saqlanadi)
            if (!r.isOk() && r.errorCode() == 429 && r.parameters() != null && r.parameters().retryAfter() != null) {
                Thread.sleep(Math.min(r.parameters().retryAfter(), 60) * 1000L);
                r = bot.execute(req);
            }
            if (!r.isOk()) log.warn("support bot {}: {} {}", req.getMethod(), r.errorCode(), r.description());
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            log.warn("support bot {}: {}", req.getMethod(), e.getMessage());
            return null;
        }
    }

    private static Result result(BaseResponse r) {
        if (r == null) return new Result(false, null, "tarmoq xatosi");
        if (!r.isOk()) return new Result(false, null, r.description());
        Integer id = r instanceof SendResponse s && s.message() != null ? s.message().messageId() : null;
        return new Result(true, id, null);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
