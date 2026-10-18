package uz.app.agro.supportbot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.billing.PlanService;
import uz.app.agro.billing.SubscriptionRepository;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.feedback.FeedbackEntity;
import uz.app.agro.feedback.FeedbackKind;
import uz.app.agro.feedback.FeedbackMessageRepository;
import uz.app.agro.feedback.FeedbackRepository;
import uz.app.agro.feedback.FeedbackSender;
import uz.app.agro.feedback.FeedbackStatus;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.security.AuthUser;
import uz.app.agro.security.UserPrincipals;
import uz.app.agro.user.UserEntity;
import uz.app.agro.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Support bot uchun bazadan o'qish/yozish — tranzaksiya chegarasi shu yerda (bridge va runner tarmoq bilan ishlaydi,
 * tranzaksiyani Telegram so'rovi davomida ochiq ushlamaydi). @PreAuthorize YO'Q — controller'ga ulanmasin.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnExpression("!'${support-bot.token:}'.isBlank()")
public class SupportStore {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackMessageRepository messageRepository;
    private final FarmerRepository farmerRepository;
    private final FieldRepository fieldRepository;
    private final UserRepository userRepository;
    private final PlanService plans;
    private final SubscriptionRepository subscriptionRepository;

    /** Guruhga yoziladigan hamma narsa — bitta tranzaksiyada (farmer, plan LAZY) */
    public record Post(long feedbackId, FeedbackKind kind, FeedbackStatus status, long farmerId, String farmerName,
                       String username, String phone, Long topicId, String planName, boolean paid, boolean unlimited,
                       LocalDate validUntil, long fields, BigDecimal areaHa, Msg message) {}

    public record Msg(FeedbackSender sender, String staffEmail, String text, String fileHashId, String photoFileId) {}

    /** Xodim (Telegram ID orqali) — huquqlari bilan: javob shu xodim nomidan yoziladi */
    public record Staff(AuthUser user, Set<GrantedAuthority> authorities) {
        static Staff of(UserEntity u) {
            // sessionId yo'q — Telegram'dan (JWT sessiyasi emas); feedback servisi ishlatmaydi
            UserPrincipals.Built b = UserPrincipals.of(u, null);
            return new Staff(b.user(), b.authorities());
        }

        UsernamePasswordAuthenticationToken authentication() {
            return new UsernamePasswordAuthenticationToken(user, null, authorities);
        }
    }

    /** Mavzudagi fermer va uning ochiq murojaati (bo'lmasa — feedbackId null) */
    public record Topic(long farmerId, String farmerName, Long feedbackId, FeedbackStatus status, Long assigneeId) {}

    public Optional<Post> post(long feedbackId, Long messageId, Long groupId) {
        FeedbackEntity f = feedbackRepository.findById(feedbackId).orElse(null);
        if (f == null) return Optional.empty();
        FarmerEntity farmer = f.getFarmer();
        Msg msg = messageId == null ? null : messageRepository.findById(messageId)
                .map(m -> new Msg(m.getSender(), m.getStaffEmail(), m.getText(), m.getFileHashId(), m.getPhotoFileId()))
                .orElse(null);
        EffectivePlan p = plans.effective(farmer.getId(), LocalDate.now());
        // Boshqa guruhdagi (SUPPORT_GROUP_ID almashgan) mavzu — yo'q hisoblanadi, yangisi ochiladi
        Long topic = groupId != null && groupId.equals(farmer.getSupportChatId()) ? farmer.getSupportTopicId() : null;
        return Optional.of(new Post(f.getId(), f.getKind(), f.getStatus(), farmer.getId(), farmer.getFullName(),
                farmer.getTelegramUsername(), farmer.getPhone(), topic, p.planName(), p.paid(),
                p.maxTotalHa() == null, p.validUntil(), fieldRepository.countByFarmerId(farmer.getId()),
                fieldRepository.sumAreaHa(farmer.getId(), -1L), msg));
    }

    /** Bridge bitta thread'da ishlaydi — bir fermerga ikkita mavzu ochilmaydi */
    @Transactional
    public void saveTopic(long farmerId, long groupId, long topicId) {
        farmerRepository.findById(farmerId).ifPresent(f -> {
            f.setSupportChatId(groupId);
            f.setSupportTopicId(topicId);
        });
    }

    public Optional<Topic> topic(long groupId, long threadId) {
        return farmerRepository.findBySupportChatIdAndSupportTopicId(groupId, threadId).map(farmer -> {
            Optional<FeedbackEntity> open = feedbackRepository.findActive(farmer.getId());
            return new Topic(farmer.getId(), farmer.getFullName(), open.map(FeedbackEntity::getId).orElse(null),
                    open.map(FeedbackEntity::getStatus).orElse(null), open.map(FeedbackEntity::getAssigneeId).orElse(null));
        });
    }

    /** Tasdiq oynasi uchun (taxminiy): to'lovning o'zi davrni BillingService'da, o'z tranzaksiyasida hisoblaydi */
    public record Period(LocalDate from, LocalDate until) {}

    public Period period(long farmerId, int durationDays, int months) {
        LocalDate today = LocalDate.now();
        LocalDate last = subscriptionRepository.lastActiveUntil(farmerId, today);
        LocalDate from = last != null ? last.plusDays(1) : today;
        return new Period(from, from.plusDays((long) durationDays * months - 1));
    }

    /** To'lovdan keyin: fermer obunasi qachongacha (oxirgisi) */
    public Period currentUntil(long farmerId) {
        LocalDate today = LocalDate.now();
        LocalDate last = subscriptionRepository.lastActiveUntil(farmerId, today);
        return last == null ? null : new Period(today, last);
    }

    public Optional<Staff> staff(long telegramUserId) {
        return userRepository.findWithPermissionsByTelegramUserId(telegramUserId)
                .filter(UserEntity::isActive)
                .map(Staff::of);
    }
}
