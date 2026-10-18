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
        return sendDetailed(chatId, text, keyboard).messageId();
    }

    /**
     * Natija sababi bilan — bildirishnoma jurnali uchun: "telegram yubormadi" emas, Telegram aytgan sabab.
     * errorCode null — tarmoq xatosi (vaqtinchalik).
     */
    public record Sent(Integer messageId, Integer errorCode, String error) {
        /** 400 — so'rovning o'zi noto'g'ri (tugma havolasi, matn): qayta yuborish befoyda */
        public boolean permanent() {
            return errorCode != null && errorCode == 400;
        }
    }

    public Sent sendDetailed(long chatId, String text, Keyboard keyboard) {
        SendMessage req = new SendMessage(chatId, text);
        if (keyboard != null) req.replyMarkup(keyboard);
        SendResponse r = execute(chatId, req);
        if (r == null) return new Sent(null, null, "tarmoq xatosi");
        return r.isOk() ? new Sent(r.message().messageId(), null, null) : new Sent(null, r.errorCode(), r.description());
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

    /**
     * Video: fileId (Telegram'da bor) yoki bayt (birinchi marta). supportsStreaming — yuklab bo'lmay turib ko'rish.
     * @return Telegram file_id (keyingi safar qayta yuklamaslik uchun) yoki null — yuborilmadi
     */
    public String sendVideo(long chatId, String fileId, byte[] content, String caption, Keyboard keyboard) {
        var req = (fileId != null ? new com.pengrad.telegrambot.request.SendVideo(chatId, fileId)
                : new com.pengrad.telegrambot.request.SendVideo(chatId, content).fileName("dropy.mp4").contentType("video/mp4"))
                .supportsStreaming(true);
        if (caption != null) req.caption(caption.length() <= 1024 ? caption : caption.substring(0, 1021) + "…");
        if (keyboard != null) req.replyMarkup(keyboard);
        SendResponse r = execute(chatId, req);
        if (r == null || !r.isOk() || r.message() == null) return null;
        return r.message().video() != null ? r.message().video().fileId()
                : r.message().document() != null ? r.message().document().fileId() : fileId;
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

    /** Faqat tugmalarni almashtirish (matn o'zgarmaydi) — belgilash tugmalari (✅) */
    public void editMarkup(long chatId, int messageId, InlineKeyboardMarkup keyboard) {
        execute(chatId, new EditMessageReplyMarkup(chatId, messageId).replyMarkup(keyboard));
    }

    /** Xabar tugmalarini olib tashlash (matn o'zgarmaydi) — eski tugma qayta bosilmasin */
    public void clearButtons(long chatId, int messageId) {
        execute(chatId, new EditMessageReplyMarkup(chatId, messageId));
    }

    /** Xabarga reaksiya ("qabul qilindi" belgisi — har xabarga matnli javob yozib chatni to'ldirmaslik uchun) */
    public void react(long chatId, int messageId, String emoji) {
        execute(chatId, new SetMessageReaction(chatId, messageId, new ReactionTypeEmoji(emoji)));
    }

    /**
     * Rasm (fileId — Telegram'da bor, yoki bayt) — @return Telegram file_id (keyingi safar qayta yuklamaslik uchun) yoki null
     */
    public String sendPhotoId(long chatId, String fileId, byte[] png, String caption, Keyboard keyboard) {
        SendPhoto req = fileId != null ? new SendPhoto(chatId, fileId) : new SendPhoto(chatId, png).fileName("dropy.png").contentType("image/png");
        if (caption != null) req.caption(caption.length() <= 1024 ? caption : caption.substring(0, 1021) + "…");
        if (keyboard != null) req.replyMarkup(keyboard);
        SendResponse r = execute(chatId, req);
        if (r == null || !r.isOk() || r.message() == null || r.message().photo() == null || r.message().photo().length == 0) return null;
        var sizes = r.message().photo();
        return sizes[sizes.length - 1].fileId();                // eng kattasi
    }

    /** Telegram lokatsiyasi (xaritada ochiladi, navigatorga "yo'l") */
    public boolean sendLocation(long chatId, double lat, double lon) {
        var r = execute(chatId, new com.pengrad.telegrambot.request.SendLocation(chatId, (float) lat, (float) lon));
        return r != null && r.isOk();
    }

    /** Chat yonidagi doimiy tugma (xabar yozish qatori chapida) — shu chat uchun */
    public boolean setMenuButton(long chatId, com.pengrad.telegrambot.model.MenuButton button) {
        var r = execute(chatId, new com.pengrad.telegrambot.request.SetChatMenuButton().chatId(chatId).menuButton(button));
        return r != null && r.isOk();
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
        if (r.isOk()) return r;
        String desc = r.description() == null ? "" : r.description();
        // 403 — botni bloklagan; 400 "chat not found" — akkaunt o'chgan: ikkalasi doimiy, qayta urinish foydasiz.
        // /start yozsa blok o'zi olinadi (FarmerRegistrationService)
        if (r.errorCode() == 403 || (r.errorCode() == 400 && desc.contains("chat not found"))) {
            log.info("telegram {} chat={}: {}", r.errorCode(), chatId, desc);
            if (chatId != null) farmers.markBlocked(chatId);
        } else if (r.errorCode() == 429) {
            log.warn("telegram 429 (limit) {}: retry_after={}s", req.getMethod(),
                    r.parameters() != null ? r.parameters().retryAfter() : null);
        } else if (!desc.contains("message is not modified")) {   // tugma ikki marta bosildi — xato emas
            log.warn("telegram {} {}: {}", req.getMethod(), r.errorCode(), desc);
        }
        return r;
    }
}
