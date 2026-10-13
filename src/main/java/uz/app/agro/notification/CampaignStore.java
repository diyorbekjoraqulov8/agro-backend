package uz.app.agro.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.notification.dto.Audience;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Kampaniya tranzaksiya chegarasi — CampaignJob'dan alohida bean (ADR #24: self-invocation proxy'ni chetlab o'tardi).
 * Har kampaniya O'Z tranzaksiyasida: bittasi buzilsa, qolganlari tiqilib qolmaydi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CampaignStore {

    private final CampaignRepository campaigns;
    private final NotificationService service;
    private final AudienceQuery audienceQuery;

    /** Auditoriyadagi har fermerga (uning tilida) xabar navbatga; bekor qilingan yoki boshqa instans olgan bo'lsa — hech narsa */
    @Transactional
    public void expand(long id) {
        campaigns.lockDue(id).ifPresent(c -> {
            Audience a = JsonMapper.shared().convertValue(c.getAudience(), Audience.class);
            List<AudienceQuery.Target> targets = audienceQuery.targets(a, null);
            for (AudienceQuery.Target t : targets) {
                Notification n = Notification.text(t.farmerId(), NotificationCategory.BROADCAST,
                        pick(c.getTexts(), t.language()), buttons(c, t.language()));
                service.enqueue(n.campaign(c.getId()));
            }
            c.setTotal(targets.size());
            c.setStartedAt(LocalDateTime.now());
            c.setStatus(targets.isEmpty() ? CampaignStatus.DONE : CampaignStatus.SENDING);
            if (targets.isEmpty()) c.setFinishedAt(LocalDateTime.now());
            log.info("kampaniya {} «{}»: {} ta fermerga navbatga qo'yildi", c.getId(), c.getTitle(), targets.size());
        });
    }

    /** Tarqatib bo'lmadi (buzuq auditoriya va h.k.) — har 10 s qayta urinib, navbatni to'sib turmasin */
    @Transactional
    public void abort(long id) {
        campaigns.abort(id);
    }

    @Transactional
    public void finishIfDelivered(long id) {
        if (campaigns.finishIfDelivered(id) > 0) log.info("kampaniya {} yakunlandi", id);
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
