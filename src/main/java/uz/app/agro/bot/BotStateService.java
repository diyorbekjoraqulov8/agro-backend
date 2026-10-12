package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BotStateService {

    private final BotStateRepository repository;

    public Optional<BotStep> current(long telegramUserId) {
        return repository.findById(telegramUserId).map(BotState::getStep);
    }

    @Transactional
    public void moveTo(long telegramUserId, BotStep step) {
        BotState s = repository.findById(telegramUserId).orElseGet(() -> {
            BotState n = new BotState();
            n.setTelegramUserId(telegramUserId);
            return n;
        });
        s.setStep(step);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
    }

    // ── Murojaat: oldingi bosqich eslab qolinadi, yuborilgach/bekor qilingach qaytariladi ──

    private static final String PREV_STEP = "prevStep", FEEDBACK_KIND = "feedbackKind", FEEDBACK_ID = "feedbackId";

    @Transactional
    public void enterFeedback(long telegramUserId, String kind) {
        enter(telegramUserId, BotStep.FEEDBACK_TEXT, FEEDBACK_KIND, kind);
    }

    /** Operator bilan suhbat rejimi: keyingi xabarlar shu suhbatga */
    @Transactional
    public void enterChat(long telegramUserId, long feedbackId) {
        enter(telegramUserId, BotStep.FEEDBACK_CHAT, FEEDBACK_ID, feedbackId);
    }

    private void enter(long telegramUserId, BotStep step, String key, Object value) {
        BotState s = repository.findById(telegramUserId).orElseGet(() -> {
            BotState n = new BotState();
            n.setTelegramUserId(telegramUserId);
            return n;
        });
        // Yangi Map — JSON ustunida joyida o'zgartirishni dirty checking sezmasligi mumkin
        Map<String, Object> data = new HashMap<>(s.getData());
        if (!isFeedback(s.getStep())) {
            data.put(PREV_STEP, s.getStep() == null ? BotStep.REPORTED.name() : s.getStep().name());
        }
        data.put(key, value);
        s.setData(data);
        s.setStep(step);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
    }

    // ── Onboarding: qaysi savolda va qaysi rejimda (start — ro'yxatdan o'tish, profile — ma'lumotlarni tahrirlash) ──

    private static final String OB_CODE = "ob", OB_MODE = "obMode";

    public record OnboardingState(String code, String mode) {}

    @Transactional
    public void enterOnboarding(long telegramUserId, String code, String mode) {
        BotState s = repository.findById(telegramUserId).orElseGet(() -> {
            BotState n = new BotState();
            n.setTelegramUserId(telegramUserId);
            return n;
        });
        Map<String, Object> data = new HashMap<>(s.getData());
        data.put(OB_CODE, code);
        data.put(OB_MODE, mode);
        s.setData(data);
        s.setStep(BotStep.ONBOARDING);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
    }

    public Optional<OnboardingState> onboarding(long telegramUserId) {
        return repository.findById(telegramUserId)
                .filter(s -> s.getStep() == BotStep.ONBOARDING && s.getData().get(OB_CODE) != null)
                .map(s -> new OnboardingState(String.valueOf(s.getData().get(OB_CODE)),
                        String.valueOf(s.getData().getOrDefault(OB_MODE, "start"))));
    }

    /** Suhbat rejimidagi murojaat id'si (JSON'dan Integer/Long bo'lib keladi) */
    public Optional<Long> chatFeedbackId(long telegramUserId) {
        return repository.findById(telegramUserId)
                .filter(s -> s.getStep() == BotStep.FEEDBACK_CHAT)
                .map(s -> s.getData().get(FEEDBACK_ID))
                .filter(Number.class::isInstance)
                .map(v -> ((Number) v).longValue());
    }

    /** Xodim yopdi: fermer aynan shu suhbat rejimida bo'lsa — chiqariladi. @return chiqarildimi */
    @Transactional
    public boolean leaveChatIf(long telegramUserId, long feedbackId) {
        if (chatFeedbackId(telegramUserId).filter(id -> id == feedbackId).isEmpty()) return false;
        leaveFeedback(telegramUserId);
        return true;
    }

    private static boolean isFeedback(BotStep step) {
        return step == BotStep.FEEDBACK_TEXT || step == BotStep.FEEDBACK_CHAT;
    }

    public Optional<String> feedbackKind(long telegramUserId) {
        return repository.findById(telegramUserId).map(s -> s.getData().get(FEEDBACK_KIND)).map(String::valueOf);
    }

    /** @return qaytarilgan bosqich */
    @Transactional
    public BotStep leaveFeedback(long telegramUserId) {
        BotState s = repository.findById(telegramUserId).orElse(null);
        if (s == null) return BotStep.REPORTED;
        if (!isFeedback(s.getStep())) return s.getStep();     // rejimda emas — bosqichga tegilmaydi
        Map<String, Object> data = new HashMap<>(s.getData());
        Object prev = data.remove(PREV_STEP);
        data.remove(FEEDBACK_KIND);
        data.remove(FEEDBACK_ID);
        BotStep step;
        try {
            step = prev == null ? BotStep.REPORTED : BotStep.valueOf(prev.toString());
        } catch (IllegalArgumentException e) {
            step = BotStep.REPORTED;
        }
        if (isFeedback(step)) step = BotStep.REPORTED;
        s.setData(data);
        s.setStep(step);
        s.setUpdatedAt(LocalDateTime.now());
        return step;
    }
}
