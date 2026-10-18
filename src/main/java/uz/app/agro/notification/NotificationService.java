package uz.app.agro.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.common.HumanDates;
import uz.app.agro.farmer.FarmerRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Bildirishnomani navbatga qo'yish — yagona kirish nuqtasi. Chaqiruvchining tranzaksiyasida yoziladi (outbox):
 * biznes o'zgarishi va xabar birga commit bo'ladi (biri yo'qolmaydi), yuborish — NotificationDispatcher.
 * @PreAuthorize YO'Q: servislar va job'lar chaqiradi — controller'ga ULANMASIN
 * (admin uchun — CampaignService, NotificationAdminService).
 *
 * Qoida: fermerning AMALIGA javob (tugma bosdi → menyu) — to'g'ridan-to'g'ri bot; TASHABBUSLI xabar
 * (hisobot tayyor, eslatma, operator javobi, kampaniya) — shu servis orqali.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int PREVIEW = 500;

    private final NotificationRepository repository;
    private final FarmerRepository farmerRepository;
    private final MessageSource messages;

    @Transactional
    public long enqueue(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.setFarmerId(n.farmerId);
        e.setChannel(n.channel);
        e.setCategory(n.category);
        e.setTemplate(n.template);
        e.getParams().putAll(n.params);
        e.setPreview(n.preview == null ? n.template : n.preview.length() <= PREVIEW ? n.preview : n.preview.substring(0, PREVIEW - 1) + "…");
        e.setRefType(n.refType);
        e.setRefId(n.refId);
        e.setCampaignId(n.campaignId);
        repository.save(e);
        return e.getId();
    }

    /** enqueueMessage + ref: takror yubormaslik uchun (NotificationRepository.existsByRefTypeAndRefId) */
    @Transactional
    public long enqueueMessageRef(long farmerId, NotificationCategory category, String refType, long refId, String key, Object... args) {
        return enqueue(message(farmerId, category, key, args).ref(refType, refId));
    }

    public boolean sent(String refType, long refId) {
        return repository.existsByRefTypeAndRefId(refType, refId);
    }

    private String render(String lang, String key, Object... args) {
        // LocalDate — fermer tilida "15-noyabr" (dd.MM.yyyy fermerga tushunarsiz)
        Object[] localized = Arrays.stream(args)
                .map(a -> a instanceof LocalDate d ? HumanDates.format(lang, d) : a).toArray();
        return messages.getMessage(key, localized, Locale.forLanguageTag(lang));
    }

    /** Bot matni (messages*.properties) fermer tilida — raqamlarni String bilan bering (MessageFormat "1 000" qilmasin), sanani LocalDate */
    @Transactional
    public long enqueueMessage(long farmerId, NotificationCategory category, String key, Object... args) {
        return enqueue(message(farmerId, category, key, args));
    }

    private Notification message(long farmerId, NotificationCategory category, String key, Object... args) {
        String lang = farmerRepository.findById(farmerId).map(f -> f.getLanguage()).orElse("uz");
        return Notification.text(farmerId, category, render(lang, key, args), List.of());
    }
}
