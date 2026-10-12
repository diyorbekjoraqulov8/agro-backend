package uz.app.agro.feedback;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;
import uz.app.agro.feedback.dto.FeedbackResponse;
import uz.app.agro.feedback.dto.MessageResponse;
import uz.app.agro.feedback.dto.OperatorStats;
import uz.app.agro.feedback.dto.ReplyRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "Murojaatlar", description = "Fermer ↔ operator suhbatlari (bot orqali)")
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private static final Set<String> SORTABLE = Set.of("createdDate", "lastMessageAt", "closedAt", "rating", "id");

    private final FeedbackService feedbackService;

    @Operation(summary = "Suhbatlar ro'yxati",
               description = "Huquq: `feedback:read`. `feedback:manage` bo'lmasa — navbat (NEW) va o'z suhbatlari. "
                       + "`mine=true` — menga biriktirilganlar. `q` — xabarlar va fermer ismi bo'yicha")
    @GetMapping
    public PageResponse<FeedbackResponse> search(
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) FeedbackKind kind,
            @RequestParam(required = false) Long farmerId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Short rating,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 25, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return feedbackService.search(status, kind, farmerId, assigneeId, rating, mine, q, Pages.guard(pageable, SORTABLE));
    }

    @Operation(summary = "Nishonlar: navbat, mening suhbatlarim, o'qilmaganlar", description = "Huquq: `feedback:read`")
    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return feedbackService.counts();
    }

    @Operation(summary = "Operatorlar statistikasi", description = "Huquq: `feedback:manage`. Ochiq/yopilgan suhbatlar, o'rtacha baho")
    @GetMapping("/operators")
    public List<OperatorStats> operators() {
        return feedbackService.operators();
    }

    @Operation(summary = "Suhbat", description = "Huquq: `feedback:read`")
    @GetMapping("/{id}")
    public FeedbackResponse get(@PathVariable Long id) {
        return feedbackService.get(id);
    }

    @Operation(summary = "Suhbat xabarlari", description = "Huquq: `feedback:read`. `after` — shu id'dan keyingilar (polling)")
    @GetMapping("/{id}/messages")
    public List<MessageResponse> messages(@PathVariable Long id, @RequestParam(required = false) Long after) {
        return feedbackService.messages(id, after);
    }

    @Operation(summary = "Javob berish — suhbatni o'ziga olish",
               description = "Huquq: `feedback:reply`. Navbatdagi suhbat shu operatorga biriktiriladi, boshqasi ololmaydi (409). "
                       + "`feedback:manage` — boshqa operatordagini ham. Audit: `FEEDBACK_CLAIMED`")
    @PostMapping("/{id}/claim")
    public FeedbackResponse claim(@PathVariable Long id) {
        return feedbackService.claim(id);
    }

    @Operation(summary = "O'qildi", description = "Huquq: `feedback:read`. Faqat suhbat egasi uchun o'qilmaganlar nolga")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id) {
        feedbackService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xabar yuborish — fermerga bot orqali",
               description = "Huquq: `feedback:reply`. Faqat OPEN: egasi yoki `feedback:manage`. Matn va/yoki `fileHashId` "
                       + "(avval `POST /files`). `delivered=false` — fermer botni bloklagan")
    @PostMapping("/{id}/messages")
    public MessageResponse send(@PathVariable Long id, @Valid @RequestBody ReplyRequest body) {
        return feedbackService.send(id, body.text(), body.fileHashId());
    }

    @Operation(summary = "Suhbatni yakunlash",
               description = "Huquq: `feedback:reply`. Fermerga xabar boradi; operator qatnashgan bo'lsa — baholash taklifi. Audit: `FEEDBACK_CLOSED`")
    @PostMapping("/{id}/close")
    public FeedbackResponse close(@PathVariable Long id) {
        return feedbackService.close(id);
    }
}
