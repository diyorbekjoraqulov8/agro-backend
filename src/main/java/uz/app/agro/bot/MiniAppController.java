package uz.app.agro.bot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    private final BotMenu menu;
    private final MiniAppToken tokens;
    private final MiniAppBoundary boundary;

    /** data — Mini App payload'i (sendData bilan bir xil JSON, ≤ 4096 bayt) */
    public record FieldRequest(@NotBlank @Size(max = 8192) String initData, @NotBlank @Size(max = 4096) String data) {}

    public record ContextRequest(@NotBlank @Size(max = 8192) String initData) {}

    /** Chat tugmasidan ochilgan Mini App: URL'da maxHa/ekin/dalalar yo'q — shu yerdan oladi */
    @Operation(summary = "Mini App konteksti", description = "Auth: Telegram initData. Tarif limiti, asosiy ekin, boshqa dalalar")
    @PostMapping("/context")
    public ResponseEntity<?> context(@Valid @RequestBody ContextRequest body) {
        Long tg = initData.verify(body.initData()).orElse(null);
        if (tg == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "INIT_DATA"));
        return ResponseEntity.ok(menu.miniAppContext(tg));
    }

    /** initData (chat/inline tugma) yoki token (pastki menyu tugmasi — bot URL'ga qo'shgan) */
    public record BoundaryRequest(@Size(max = 8192) String initData, @Size(max = 200) String token,
                                  @DecimalMin("-60") @DecimalMax("75") double lat, @DecimalMin("-180") @DecimalMax("180") double lon) {}

    @Operation(summary = "🪄 Dala chegarasini aniqlash", description = "Auth: initData yoki Mini App tokeni. Fermer boshiga soatiga "
            + MiniAppBoundary.LIMIT + " ta. outcome: OK | RATE_LIMIT | BUSY | UNAVAILABLE | NO_SCENES | NO_CLEAR_SCENE | POINT_NOT_CLEAR | ...")
    @PostMapping("/boundary")
    public ResponseEntity<Map<String, Object>> boundary(@Valid @RequestBody BoundaryRequest body) {
        Long tg = body.initData() != null && !body.initData().isBlank() ? initData.verify(body.initData()).orElse(null)
                : tokens.verify(body.token()).orElse(null);
        if (tg == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "AUTH"));
        // Faqat ro'yxatdan o'tgan fermer: Python (Sentinel-2) so'rovini begona Telegram foydalanuvchisi ishga tushirmasin
        if (!menu.isFarmer(tg)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "outcome", "NOT_REGISTERED"));
        var r = boundary.detect(tg, body.lat(), body.lon());
        if (r.boundary() == null) {
            HttpStatus status = "RATE_LIMIT".equals(r.outcome()) || "BUSY".equals(r.outcome()) ? HttpStatus.TOO_MANY_REQUESTS
                    : "UNAVAILABLE".equals(r.outcome()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNPROCESSABLE_CONTENT;
            return ResponseEntity.status(status).body(Map.of("ok", false, "outcome", r.outcome()));
        }
        var b = r.boundary();
        return ResponseEntity.ok(Map.of("ok", true, "outcome", "OK", "coordinates", b.coordinates(), "areaHa", b.areaHa(),
                "sceneDate", b.sceneDate().toString(), "confidence", b.confidence()));
    }

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
