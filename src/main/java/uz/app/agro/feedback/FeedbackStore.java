package uz.app.agro.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.notification.Templates;
import uz.app.agro.feedback.dto.MessageResponse;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.security.AuthUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Tranzaksiya chegarasi ALOHIDA bean'da (ADR #24): xabar va uning bildirishnomasi (outbox) birga commit bo'ladi;
 * yetkazilgani — FeedbackDeliveryListener orqali (markDelivered).
 */
@Component
@RequiredArgsConstructor
class FeedbackStore {

    /** Bildirishnoma ref turi — yetkazilganda chat xabari "✓✓" bo'ladi (FeedbackDeliveryListener) */
    static final String REF_MESSAGE = "FEEDBACK_MESSAGE";

    private final FeedbackRepository feedbackRepository;
    private final FeedbackMessageRepository messageRepository;
    private final AuditService auditService;
    private final FeedbackMapper mapper;
    private final FileRegistry files;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    /** Faqat OPEN suhbatga: egasi (operator) yoki feedback:manage */
    @Transactional
    public MessageResponse saveStaffMessage(Long id, String text, String fileHashId, AuthUser actor, boolean manage) {
        FeedbackEntity f = require(id, actor, manage);
        if (f.getStatus() == FeedbackStatus.NEW) throw new ConflictException("Avval suhbatni oling — «Javob berish» tugmasi");
        if (f.getStatus() == FeedbackStatus.CLOSED) throw new ConflictException("Suhbat yakunlangan");
        boolean own = actor.id().equals(f.getAssigneeId());
        if (!own && !manage) throw new ConflictException("Bu suhbat boshqa operatorda");
        FeedbackMessageEntity m = new FeedbackMessageEntity();
        m.setFeedbackId(id);
        m.setSender(FeedbackSender.STAFF);
        m.setStaffId(actor.id());
        m.setStaffEmail(actor.email());
        m.setText(text);
        m.setFileHashId(fileHashId);
        messageRepository.save(m);
        f.setLastMessageAt(m.getCreatedDate());
        f.setLastSender(FeedbackSender.STAFF);
        if (own) f.setUnreadStaff(0);           // javob yozdi — o'qigan; admin yozsa operator nishoni qoladi
        // Outbox: xabar va uni yuborish — bitta tranzaksiyada
        notifications.enqueue(Notification.template(f.getFarmer().getId(), NotificationCategory.FEEDBACK, Templates.FEEDBACK_REPLY)
                .param("feedbackId", id).param("text", text).param("fileHashId", fileHashId)
                .ref(REF_MESSAGE, m.getId())
                .preview("Murojaat №" + id + " javobi: " + (text != null ? text : "📎 fayl")));
        events.publishEvent(FeedbackActivityEvent.staff(id, m.getId()));
        return mapper.toResponse(m, fileHashId == null ? Map.of() : files.infos(List.of(fileHashId)));
    }

    /**
     * NotificationDeliveredEvent'dan (commit'dan keyin) — chat xabari "✓✓" yoki "yetmadi".
     * REQUIRES_NEW: AFTER_COMMIT listener'da REQUIRED tugagan tranzaksiyaga qo'shiladi va o'zgarish jimgina yo'qoladi
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markDelivered(long messageId, boolean delivered) {
        messageRepository.findById(messageId).ifPresent(m -> m.setDelivered(delivered));
    }

    /** NEW (spam/takror) — reply huquqli har kim; OPEN — egasi yoki manage */
    @Transactional
    public void close(Long id, AuthUser actor, boolean manage) {
        FeedbackEntity f = require(id, actor, manage);
        if (f.getStatus() == FeedbackStatus.CLOSED) throw new ConflictException("Suhbat allaqachon yakunlangan");
        if (f.getStatus() == FeedbackStatus.OPEN && !actor.id().equals(f.getAssigneeId()) && !manage) {
            throw new ConflictException("Bu suhbat boshqa operatorda");
        }
        f.setStatus(FeedbackStatus.CLOSED);
        f.setClosedAt(LocalDateTime.now());
        f.setClosedBy(FeedbackSender.STAFF);
        f.setClosedByEmail(actor.email());
        f.setUnreadStaff(0);
        messageRepository.save(system(id, actor.email() + " suhbatni yakunladi"));
        auditService.event(AuditEvent.FEEDBACK_CLOSED).target("FEEDBACK", id)
                .detail("farmerId", f.getFarmer().getId())
                .detail("assignee", f.getAssigneeEmail())
                .record();
        notifications.enqueue(Notification.template(f.getFarmer().getId(), NotificationCategory.FEEDBACK, Templates.FEEDBACK_CLOSED)
                .param("feedbackId", id).param("askRating", f.getAssigneeId() != null)
                .preview("Murojaat №" + id + " yakunlandi"));
        events.publishEvent(FeedbackActivityEvent.closed(id));
    }

    /** DTO tranzaksiya ichida — farmer LAZY */
    @Transactional(readOnly = true)
    public uz.app.agro.feedback.dto.FeedbackResponse view(Long id) {
        return mapper.toResponse(feedbackRepository.findById(id).orElseThrow(() -> new NotFoundException("Murojaat", id)));
    }

    /** Visibility: manage — hammasi; aks holda navbat (NEW) va o'zining suhbatlari. Begonasi — 404 (ADR #16) */
    FeedbackEntity require(Long id, AuthUser actor, boolean manage) {
        FeedbackEntity f = feedbackRepository.findById(id).orElseThrow(() -> new NotFoundException("Murojaat", id));
        if (!manage && f.getStatus() != FeedbackStatus.NEW && !actor.id().equals(f.getAssigneeId())) {
            throw new NotFoundException("Murojaat", id);
        }
        return f;
    }

    static FeedbackMessageEntity system(Long feedbackId, String text) {
        FeedbackMessageEntity m = new FeedbackMessageEntity();
        m.setFeedbackId(feedbackId);
        m.setSender(FeedbackSender.SYSTEM);
        m.setText(text);
        return m;
    }

}
