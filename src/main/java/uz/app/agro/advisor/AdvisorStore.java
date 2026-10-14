package uz.app.agro.advisor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.notification.Templates;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tranzaksiya chegarasi — job'dan alohida bean (ADR #24). Claude (tarmoq) bu yerda CHAQIRILMAYDI.
 * Natija yozuvi va fermerga xabar — bitta tranzaksiyada (outbox).
 */
@Component
@RequiredArgsConstructor
class AdvisorStore {

    /** 429/5xx dan keyin: 1-urinish → 1 daq, 2 → 5 daq */
    private static final Duration[] BACKOFF = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)};
    /**
     * Olingan so'rov band (boshqa instans olmaydi); ilova yiqilsa — muddatdan keyin qaytadi. Bittadan olinadi va
     * SDK qayta urinmaydi (maxRetries 0) — bitta chaqiruv ≤ advisor.timeout (120 s), lease undan katta bo'lishi shart
     */
    private static final Duration LEASE = Duration.ofMinutes(5);

    record Input(long id, String photoHashId, ClaudeAdvisor.Context context) {}

    private final AdvisorRequestRepository requests;
    private final FarmerRepository farmers;
    private final FieldRepository fields;
    private final NotificationService notifications;

    /** Navbatdan bitta (SKIP LOCKED) — lease shu so'rovning o'zi uchun boshlanadi */
    @Transactional
    public Optional<Long> claimNext() {
        List<Long> ids = requests.lockDue(1);
        if (ids.isEmpty()) return Optional.empty();
        requests.findById(ids.getFirst()).ifPresent(r -> r.setNextAttemptAt(LocalDateTime.now().plus(LEASE)));
        return Optional.of(ids.getFirst());
    }

    /** Claude'ga yuboriladigan kontekst — tranzaksiya ichida o'qiladi */
    @Transactional(readOnly = true)
    public Optional<Input> input(long id) {
        return requests.findById(id).filter(r -> r.getStatus() == AdvisorRequestEntity.Status.PENDING).map(r -> {
            FarmerEntity farmer = farmers.findById(r.getFarmerId()).orElse(null);
            FieldEntity field = r.getFieldId() == null ? null : fields.findById(r.getFieldId()).orElse(null);
            String crop = field != null ? field.getCrop().name()
                    : farmer != null && farmer.getMainCrop() != null ? farmer.getMainCrop().name() : "unknown";
            String country = farmer != null && farmer.getCountryCode() != null ? farmer.getCountryCode() : "UZ";
            String location = field != null && field.getCentroidLat() != null
                    ? coord(field.getCentroidLat(), "N", "S") + " " + coord(field.getCentroidLon(), "E", "W") + ", " + country
                    : country;
            return new Input(id, r.getPhotoHashId(), new ClaudeAdvisor.Context(r.getLanguage(), crop, location,
                    LocalDate.now().toString(), r.getFarmerNote()));
        });
    }

    /** AI javob berdi — natija + fermerga xabar. Faqat PENDING (lease o'tib boshqa worker yozgan bo'lsa — ikki marta yo'q) */
    @Transactional
    public void answered(long id, ClaudeAdvisor.Result result) {
        AdvisorRequestEntity r = requests.findById(id).orElse(null);
        if (r == null || r.getStatus() != AdvisorRequestEntity.Status.PENDING) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> json = JsonMapper.shared().convertValue(result.diagnosis(), Map.class);
        r.setAiResult(json);
        r.setAiModel(result.model());
        r.setInputTokens((int) result.inputTokens());
        r.setOutputTokens((int) result.outputTokens());
        r.setAiError(null);
        r.setStatus(AdvisorRequestEntity.Status.ANSWERED);
        r.setAnsweredAt(LocalDateTime.now());
        notify(r, Templates.ADVISOR_ANSWER, "AI tavsiyasi: " + result.diagnosis().category() + " — " + Texts.cut(result.diagnosis().summary(), 300));
    }

    /**
     * Xato. retryable va urinishlar qolgan — keyinroq qayta; aks holda FAILED: fermerga "qayta yuboring"
     * (limitdan ayirilmaydi). Faqat PENDING — javob berilganini FAILED qilib yubormaslik uchun.
     */
    @Transactional
    public void failed(long id, String error, boolean retryable, int maxAttempts) {
        AdvisorRequestEntity r = requests.findById(id).orElse(null);
        if (r == null || r.getStatus() != AdvisorRequestEntity.Status.PENDING) return;
        r.setAttempts(r.getAttempts() + 1);
        r.setAiError(Texts.cut(error, 500));
        if (retryable && r.getAttempts() < maxAttempts) {
            r.setNextAttemptAt(LocalDateTime.now().plus(BACKOFF[Math.min(r.getAttempts() - 1, BACKOFF.length - 1)]));
            return;
        }
        r.setStatus(AdvisorRequestEntity.Status.FAILED);
        notify(r, Templates.ADVISOR_ANSWER, "AI javob bermadi — fermer qayta yuboradi");
    }

    private void notify(AdvisorRequestEntity r, String template, String preview) {
        notifications.enqueue(Notification.template(r.getFarmerId(), NotificationCategory.ADVISOR, template)
                .param("requestId", r.getId()).ref("ADVISOR_REQUEST", r.getId()).preview(preview));
    }

    private static String coord(BigDecimal v, String pos, String neg) {
        return v.abs().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + (v.signum() >= 0 ? pos : neg);
    }
}
