package uz.app.agro.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vaqti kelgan kampaniyalarni tarqatadi (CampaignStore — har biri alohida tranzaksiyada); yuborish —
 * NotificationDispatcher (limit bilan). Hammasi yetkazilgach — DONE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
class CampaignJob {

    private final CampaignRepository campaigns;
    private final CampaignStore store;

    @Scheduled(fixedDelayString = "${notification.campaign.interval:10s}", initialDelayString = "${notification.dispatcher.initial-delay:10s}")
    public void tick() {
        for (Long id : campaigns.dueIds(5)) {
            try {
                store.expand(id);
            } catch (RuntimeException e) {
                log.error("kampaniya {} tarqatilmadi — bekor qilindi", id, e);
                store.abort(id);
            }
        }
        campaigns.sendingIds().forEach(store::finishIfDelivered);
    }
}
