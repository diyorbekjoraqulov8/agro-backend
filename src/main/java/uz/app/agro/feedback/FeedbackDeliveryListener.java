package uz.app.agro.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.app.agro.notification.NotificationDeliveredEvent;

/** Operator xabari fermerga yetdi/yetmadi — admin chatida ✓✓ yoki "yetmadi" (bildirishnoma commit'idan keyin) */
@Component
@RequiredArgsConstructor
class FeedbackDeliveryListener {

    private final FeedbackStore store;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelivered(NotificationDeliveredEvent e) {
        if (FeedbackStore.REF_MESSAGE.equals(e.refType()) && e.refId() != null) store.markDelivered(e.refId(), e.delivered());
    }
}
