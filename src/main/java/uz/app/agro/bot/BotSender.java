package uz.app.agro.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.model.reaction.ReactionTypeEmoji;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SetMessageReaction;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.farmer.FarmerRegistrationService;

/**
 * Telegram'ga yuborish. HECH QACHON exception tashlamaydi — bitta yuborilmagan xabar
 * update'ni (va DB'dagi holatni) buzmasin. Tranzaksiya ICHIDA chaqirilmasin (tarmoq).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotSender {

    private final TelegramBot bot;
    private final FarmerRegistrationService farmers;

    /** @return yuborilgan xabar id'si yoki null */
    public Integer send(long chatId, String text, Keyboard keyboard) {
        SendMessage req = new SendMessage(chatId, text);
        if (keyboard != null) req.replyMarkup(keyboard);
        SendResponse r = execute(chatId, req);
        return r != null && r.isOk() ? r.message().messageId() : null;
    }

    /** caption ≤ 1024 belgi (Telegram) — oshsa kesiladi */
    public boolean sendPhoto(long chatId, byte[] png, String caption, Keyboard keyboard) {
        SendPhoto req = new SendPhoto(chatId, png).fileName("agro.png").contentType("image/png");
        if (caption != null) req.caption(caption.length() <= 1024 ? caption : caption.substring(0, 1021) + "…");
        if (keyboard != null) req.replyMarkup(keyboard);
        SendResponse r = execute(chatId, req);
        return r != null && r.isOk();
    }

    /** Telegram'dagi mavjud rasm (file_id) — qayta yuklamasdan */
    public boolean sendPhoto(long chatId, String fileId, String caption) {
        SendPhoto req = new SendPhoto(chatId, fileId);
        if (caption != null) req.caption(caption.length() <= 1024 ? caption : caption.substring(0, 1021) + "…");
        SendResponse r = execute(chatId, req);
        return r != null && r.isOk();
    }

    public boolean sendDocument(long chatId, byte[] content, String fileName, String contentType, String caption) {
        SendDocument req = new SendDocument(chatId, content).fileName(fileName).contentType(contentType);
        if (caption != null) req.caption(caption.length() <= 1024 ? caption : caption.substring(0, 1021) + "…");
        SendResponse r = execute(chatId, req);
        return r != null && r.isOk();
    }

    /** Fayl (bayt) — rasm sendPhoto, qolgani sendDocument; caption ≤ 1024 (oshsa kesiladi) */
    public boolean sendFile(long chatId, byte[] content, String fileName, String contentType, boolean photo,
                            String caption, Keyboard keyboard) {
        String c = caption == null ? null : caption.length() <= 1024 ? caption : caption.substring(0, 1021) + "…";
        SendResponse r;
        if (photo) {
            SendPhoto req = new SendPhoto(chatId, content).fileName(fileName).contentType(contentType);
            if (c != null) req.caption(c);
            if (keyboard != null) req.replyMarkup(keyboard);
            r = execute(chatId, req);
        } else {
            SendDocument req = new SendDocument(chatId, content).fileName(fileName).contentType(contentType);
            if (c != null) req.caption(c);
            if (keyboard != null) req.replyMarkup(keyboard);
            r = execute(chatId, req);
        }
        return r != null && r.isOk();
    }

    public void edit(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText req = new EditMessageText(chatId, messageId, text);
        if (keyboard != null) req.replyMarkup(keyboard);
        execute(chatId, req);
    }

    /** Xabar tugmalarini olib tashlash (matn o'zgarmaydi) — eski tugma qayta bosilmasin */
    public void clearButtons(long chatId, int messageId) {
        execute(chatId, new EditMessageReplyMarkup(chatId, messageId));
    }

    /** Xabarga reaksiya ("qabul qilindi" belgisi — har xabarga matnli javob yozib chatni to'ldirmaslik uchun) */
    public void react(long chatId, int messageId, String emoji) {
        execute(chatId, new SetMessageReaction(chatId, messageId, new ReactionTypeEmoji(emoji)));
    }

    /** Callback'ga javob SHART — aks holda tugmada soat belgisi ~15 s aylanib turadi. */
    public void answer(String callbackId, String toast) {
        AnswerCallbackQuery req = new AnswerCallbackQuery(callbackId);
        if (toast != null) req.text(toast);
        execute(null, req);
    }

    private <T extends BaseRequest<T, R>, R extends BaseResponse> R execute(Long chatId, BaseRequest<T, R> req) {
        R r;
        try {
            r = bot.execute(req);
        } catch (RuntimeException e) {                 // tarmoq (OkHttp) xatosi
            log.warn("telegram {} yuborilmadi: {}", req.getMethod(), e.getMessage());
            return null;
        }
        if (!r.isOk()) {
            switch (r.errorCode()) {
                case 403 -> {                          // fermer botni bloklagan
                    log.info("telegram 403 chat={}: {}", chatId, r.description());
                    if (chatId != null) farmers.markBlocked(chatId);
                }
                case 429 -> log.warn("telegram 429 (limit) {}: retry_after={}s", req.getMethod(),
                        r.parameters() != null ? r.parameters().retryAfter() : null);
                default -> {
                    // Fermer bir tugmani ikki marta bossa — matn o'zgarmagan, xato emas
                    if (r.description() == null || !r.description().contains("message is not modified")) {
                        log.warn("telegram {} {}: {}", req.getMethod(), r.errorCode(), r.description());
                    }
                }
            }
        }
        return r;
    }
}
