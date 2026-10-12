package uz.app.agro.bot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Inline tugmadan ochilgan Mini App dalani shu yerga yuboradi (sendData faqat reply-klaviaturadan ishlaydi).
 * Autentifikatsiya — Telegram initData imzosi (cookie/JWT yo'q); SecurityConfig.miniAppChain: CSRF yo'q, CORS — Mini App domeni.
 * Natija fermerga bot xabari bilan ham boradi (sendData yo'li bilan bir xil — BotUpdateHandler.submitPolygon).
 */
@Slf4j
@Tag(name = "Mini App", description = "Telegram Mini App → server (initData imzosi bilan)")
@RestController
@RequestMapping("/miniapp")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class MiniAppController {

    /** Mini App "yuborildi" deb yopiladigan natijalar (qolganida — xato matni bot chatida, Mini App ochiq qoladi) */
    private static final Set<String> ACCEPTED = Set.of("QUEUED", "QUOTA_USED");

    private final BotInitData initData;
    private final BotUpdateHandler handler;

    /** data — Mini App payload'i (sendData bilan bir xil JSON, ≤ 4096 bayt) */
    public record FieldRequest(@NotBlank @Size(max = 8192) String initData, @NotBlank @Size(max = 4096) String data) {}

    @Operation(summary = "Dala chegarasi (inline tugmadan)", description = "Auth: Telegram initData (HMAC). 401 — imzo noto'g'ri yoki eskirgan")
    @PostMapping("/fields")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody FieldRequest body) {
        Long tg = initData.verify(body.initData()).orElse(null);
        if (tg == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "INIT_DATA"));
        }
        // Shaxsiy chat: chat_id == telegram_user_id
        String outcome = handler.submitPolygon(tg, tg, body.data());
        log.info("mini app (HTTP): tg {} → {}", tg, outcome);
        return ResponseEntity.ok(Map.of("ok", ACCEPTED.contains(outcome), "outcome", outcome));
    }
}
