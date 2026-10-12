package uz.app.agro.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.notification.dto.Audience;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Vaqti kelgan kampaniyani tarqatadi: auditoriyadagi har fermerga (uning tilida) xabar navbatga — bitta
 * tranzaksiyada; yuborish — NotificationDispatcher (limit bilan). Hammasi yetkazilgach — DONE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
class CampaignJob {

    private final CampaignRepository campaigns;
    private final NotificationRepository notifications;
    private final NotificationService service;
    private final AudienceQuery audienceQuery;

    @Scheduled(fixedDelayString = "${notification.campaign.interval:10s}", initialDelayString = "${notification.dispatcher.initial-delay:10s}")
    @Transactional
    public void tick() {
        for (CampaignEntity c : campaigns.lockDue(5)) expand(c);
        for (CampaignEntity c : campaigns.findByStatus(CampaignStatus.SENDING)) {
            if (notifications.campaignOpen(c.getId()) == 0) {
                c.setStatus(CampaignStatus.DONE);
                c.setFinishedAt(LocalDateTime.now());
                log.info("kampaniya {} yakunlandi", c.getId());
            }
        }
    }

    private void expand(CampaignEntity c) {
        Audience a = JsonMapper.shared().convertValue(c.getAudience(), Audience.class);
        List<AudienceQuery.Target> targets = audienceQuery.targets(a, null);
        for (AudienceQuery.Target t : targets) {
            Notification n = Notification.text(t.farmerId(), NotificationCategory.BROADCAST, pick(c.getTexts(), t.language()), buttons(c, t.language()));
            service.enqueue(n.campaign(c.getId()));
        }
        c.setTotal(targets.size());
        c.setStartedAt(LocalDateTime.now());
        c.setStatus(targets.isEmpty() ? CampaignStatus.DONE : CampaignStatus.SENDING);
        if (targets.isEmpty()) c.setFinishedAt(LocalDateTime.now());
        log.info("kampaniya {} «{}»: {} ta fermerga navbatga qo'yildi", c.getId(), c.getTitle(), targets.size());
    }

    private static List<Notification.Button> buttons(CampaignEntity c, String lang) {
        if (c.getButtonUrl() == null || c.getButtonTexts() == null) return List.of();
        return List.of(new Notification.Button(pick(c.getButtonTexts(), lang), c.getButtonUrl()));
    }

    /** Fermer tilida; bo'lmasa o'zbekcha */
    private static String pick(Map<String, String> texts, String lang) {
        String t = texts.get(lang);
        return t != null && !t.isBlank() ? t : texts.get("uz");
    }
}
