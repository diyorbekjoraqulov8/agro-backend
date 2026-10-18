package uz.app.agro.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.feedback.dto.FeedbackResponse;
import uz.app.agro.feedback.dto.MessageResponse;
import uz.app.agro.feedback.dto.OperatorStats;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.file.FileSource;
import uz.app.agro.file.dto.FileResponse;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.security.AuthUser;
import uz.app.agro.security.CurrentActor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Xodimlar: navbat → "Javob berish" (claim — boshqa operator endi ololmaydi) → yozishma → yakunlash.
 * Ko'rinish: feedback:manage — barcha suhbatlar; qolganlar — navbat va o'z suhbatlari (begonasi 404).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {

    private static final int THREAD_LIMIT = 500;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackMessageRepository messageRepository;
    private final FeedbackStore store;
    private final FeedbackMapper mapper;
    private final AuditService auditService;
    private final FileRegistry files;

    /** @param mine true — faqat menga biriktirilganlar (assigneeId e'tiborsiz) */
    @PreAuthorize(Permissions.CAN_READ_FEEDBACK)
    public PageResponse<FeedbackResponse> search(FeedbackStatus status, boolean active, FeedbackKind kind, Long farmerId,
                                                 Long assigneeId, Short rating, boolean mine, String q, Pageable pageable) {
        AuthUser me = actor();
        Long visibleTo = manage(me) ? null : me.id();
        Long assignee = mine ? me.id() : assigneeId;
        return PageResponse.of(feedbackRepository.findAll(
                FeedbackSpecs.filter(status, active, kind, farmerId, assignee, rating, q, visibleTo), pageable).map(mapper::toResponse));
    }

    @PreAuthorize(Permissions.CAN_READ_FEEDBACK)
    public FeedbackResponse get(Long id) {
        AuthUser me = actor();
        return mapper.toResponse(store.require(id, me, manage(me)));
    }

    /** @param after — shu id'dan keyingilar (polling); null — boshidan */
    @PreAuthorize(Permissions.CAN_READ_FEEDBACK)
    public List<MessageResponse> messages(Long id, Long after) {
        AuthUser me = actor();
        store.require(id, me, manage(me));
        List<FeedbackMessageEntity> list = messageRepository.findByFeedbackIdAndIdGreaterThanOrderByIdAsc(id, after == null ? 0L : after)
                .stream().limit(THREAD_LIMIT).toList();
        // Fayllar bitta so'rovda (N+1 emas)
        Map<String, FileResponse> fileInfos = files.infos(list.stream().map(FeedbackMessageEntity::getFileHashId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return list.stream().map(m -> mapper.toResponse(m, fileInfos)).toList();
    }

    /** Sidebar va tablar nishoni: navbat, mening ochiq suhbatlarim, ulardagi o'qilmaganlar */
    @PreAuthorize(Permissions.CAN_READ_FEEDBACK)
    public Map<String, Long> counts() {
        AuthUser me = actor();
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("NEW", feedbackRepository.countByStatus(FeedbackStatus.NEW));
        m.put("MINE", feedbackRepository.countByStatusAndAssigneeId(FeedbackStatus.OPEN, me.id()));
        m.put("MINE_UNREAD", feedbackRepository.unreadFor(me.id()));
        if (manage(me)) m.put("OPEN", feedbackRepository.countByStatus(FeedbackStatus.OPEN));
        // Javob kutayotgan obuna so'rovlari (navbatdagi + oxirgi xabar fermerdan) — "💎 Obuna so'rovlari" nishoni
        m.put("SALES", manage(me) ? feedbackRepository.countSalesWaiting() : feedbackRepository.countSalesWaitingFor(me.id()));
        return m;
    }

    @PreAuthorize(Permissions.CAN_MANAGE_FEEDBACK)
    public List<OperatorStats> operators() {
        return feedbackRepository.operators().stream()
                .map(r -> new OperatorStats(r.getStaffId(), r.getEmail(), r.getOpenCount(), r.getClosedCount(),
                        r.getRatedCount(), r.getAvgRating()))
                .toList();
    }

    /**
     * "Javob berish": navbatdagi suhbatni o'ziga olish. Atomik — ikki operator bir vaqtda bossa, biri 409 oladi.
     * feedback:manage — boshqa operatordagi suhbatni ham o'ziga ola oladi (operator ishda bo'lmasa).
     */
    @PreAuthorize(Permissions.CAN_REPLY_FEEDBACK)
    @Transactional
    public FeedbackResponse claim(Long id) {
        AuthUser me = actor();
        boolean manage = manage(me);
        FeedbackEntity f = store.require(id, me, manage);
        if (f.getStatus() == FeedbackStatus.CLOSED) throw new ConflictException("Suhbat yakunlangan");
        String previous = null;
        if (f.getStatus() == FeedbackStatus.NEW) {
            if (feedbackRepository.claim(id, me.id(), me.email(), LocalDateTime.now()) == 0) {
                FeedbackEntity now = feedbackRepository.findById(id).orElseThrow(() -> new NotFoundException("Murojaat", id));
                if (!me.id().equals(now.getAssigneeId())) {
                    throw new ConflictException("Bu murojaatni hozirgina boshqa operator oldi" +
                            (now.getAssigneeEmail() != null ? " (" + now.getAssigneeEmail() + ")" : ""));
                }
                return mapper.toResponse(now);
            }
        } else if (me.id().equals(f.getAssigneeId())) {
            return mapper.toResponse(f);                      // allaqachon meniki — idempotent
        } else if (manage) {
            previous = f.getAssigneeEmail();
            f.setAssigneeId(me.id());
            f.setAssigneeEmail(me.email());
            f.setAssignedAt(LocalDateTime.now());
        } else {
            throw new ConflictException("Bu suhbat boshqa operatorda");
        }
        messageRepository.save(FeedbackStore.system(id, previous == null
                ? me.email() + " suhbatni oldi"
                : me.email() + " suhbatni o'ziga oldi (oldin: " + previous + ")"));
        auditService.event(AuditEvent.FEEDBACK_CLAIMED).target("FEEDBACK", id)
                .detail("previous", previous)
                .record();
        return mapper.toResponse(feedbackRepository.findById(id).orElseThrow());
    }

    /** Operator suhbatni ochdi — o'qilmaganlar nolga. Faqat egasi: admin ko'rishi operator nishonini o'chirmasin */
    @PreAuthorize(Permissions.CAN_READ_FEEDBACK)
    @Transactional
    public void markRead(Long id) {
        AuthUser me = actor();
        FeedbackEntity f = store.require(id, me, manage(me));
        if (me.id().equals(f.getAssigneeId()) && f.getUnreadStaff() != 0) f.setUnreadStaff(0);
    }

    /**
     * Xabar: saqlanadi va fermerga yuborish navbatga qo'yiladi — bitta tranzaksiyada (outbox). delivered = null
     * ("yuborilmoqda"); yetkazilgach NotificationDeliveredEvent → FeedbackDeliveryListener belgilaydi.
     */
    @PreAuthorize(Permissions.CAN_REPLY_FEEDBACK)
    @Transactional
    public MessageResponse send(Long id, String text, String fileHashId) {
        AuthUser me = actor();
        String clean = FeedbackBotService.clean(text);
        if (clean == null && fileHashId == null) throw new BadRequestException("Matn yozing yoki fayl biriktiring");
        if (fileHashId != null) {
            // Faqat o'zi yuklagan fayl — boshqa suhbatdagi (begona) faylni hashId bilan fermerga jo'natib bo'lmasin
            files.find(fileHashId)
                    .filter(f -> f.getSource() == FileSource.STAFF && me.id().equals(f.getUploadedById()))
                    .orElseThrow(() -> new BadRequestException("Fayl topilmadi — qayta yuklang"));
        }
        return store.saveStaffMessage(id, clean, fileHashId, me, manage(me));
    }

    /** Yakunlash: fermerga xabar va (operator qatnashgan bo'lsa) baholash taklifi — navbat orqali */
    @PreAuthorize(Permissions.CAN_REPLY_FEEDBACK)
    @Transactional
    public FeedbackResponse close(Long id) {
        AuthUser me = actor();
        store.close(id, me, manage(me));
        return store.view(id);
    }

    private static AuthUser actor() {
        // @PreAuthorize o'tgan — autentifikatsiya bor; yo'q bo'lsa bu dasturchi xatosi
        return CurrentActor.get().orElseThrow(() -> new IllegalStateException("autentifikatsiya yo'q"));
    }

    private static boolean manage(AuthUser u) {
        return u.permissions() != null && u.permissions().contains(Permissions.FEEDBACK_MANAGE);
    }
}
