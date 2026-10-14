package uz.app.agro.advisor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.billing.PlanService;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Bot "🤖 AI maslahat": limit, so'rovni navbatga qo'yish, natijani o'qish.
 * @PreAuthorize YO'Q — bot chaqiradi, controller'ga ULANMASIN. Egalik tekshiriladi (telegramUserId) — callback ishonchsiz.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdvisorBotService {

    public enum Access { ALLOWED, DISABLED, FREE_USED, DAILY_LIMIT }

    /** limit — tarif chegarasi (xabarda ko'rsatish uchun) */
    public record Quota(Access access, int limit) {}

    /** Bot ko'rsatadigan holat: diagnosis — AI javobi (FAILED da yo'q) */
    public record View(long id, AdvisorRequestEntity.Status status, AdvisorDiagnosis diagnosis) {}

    private final AdvisorRequestRepository requests;
    private final FarmerRepository farmers;
    private final FieldRepository fields;
    private final PlanService plans;
    private final SettingsService settings;
    private final AdvisorProperties props;

    public Quota quota(long telegramUserId) {
        return farmers.findByTelegramUserId(telegramUserId).map(this::quota).orElse(new Quota(Access.DISABLED, 0));
    }

    /** AI javob bermagani (FAILED) hisoblanmaydi — fermer aybsiz, qayta yuboradi */
    private Quota quota(FarmerEntity f) {
        if (!settings.getBool(Setting.ADVISOR_ENABLED) || !props.configured()) return new Quota(Access.DISABLED, 0);
        var failed = AdvisorRequestEntity.Status.FAILED;
        if (plans.paidOn(f.getId(), LocalDate.now())) {
            int daily = settings.getInt(Setting.ADVISOR_PAID_DAILY);
            long today = requests.countByFarmerIdAndStatusNotAndCreatedDateAfter(f.getId(), failed, LocalDate.now().atStartOfDay());
            return new Quota(today < daily ? Access.ALLOWED : Access.DAILY_LIMIT, daily);
        }
        int free = settings.getInt(Setting.ADVISOR_FREE_TOTAL);
        return new Quota(requests.countByFarmerIdAndStatusNot(f.getId(), failed) < free ? Access.ALLOWED : Access.FREE_USED, free);
    }

    /**
     * Navbatga — AdvisorJob 3 s ichida oladi. Limit shu yerda qayta tekshiriladi (tugmani ikki marta bosish).
     * @param fieldId — fermerning dalasi bo'lsagina (begona id — e'tiborsiz)
     * @return so'rov id; bo'sh — limit/o'chiq
     */
    @Transactional
    public Optional<Long> submit(long telegramUserId, Long fieldId, String photoHashId, String note) {
        FarmerEntity f = farmers.findByTelegramUserId(telegramUserId).orElse(null);
        if (f == null || quota(f).access() != Access.ALLOWED) return Optional.empty();
        Long ownField = fieldId == null ? null : fields.findById(fieldId)
                .filter(x -> x.getFarmer().getId().equals(f.getId())).map(x -> x.getId()).orElse(null);
        AdvisorRequestEntity r = new AdvisorRequestEntity();
        r.setFarmerId(f.getId());
        r.setFieldId(ownField);
        r.setPhotoHashId(photoHashId);
        r.setFarmerNote(note == null || note.isBlank() ? null : Texts.cut(note.strip(), 1000));
        r.setLanguage(f.getLanguage());
        r.setNextAttemptAt(LocalDateTime.now());
        r.setCreatedDate(LocalDateTime.now());
        return Optional.of(requests.save(r).getId());
    }

    /** Bildirishnoma renderi — fermerning o'z so'rovi bo'lsagina */
    public Optional<View> view(long telegramUserId, long requestId) {
        return requests.findById(requestId)
                .filter(r -> farmers.findById(r.getFarmerId()).map(f -> f.getTelegramUserId() == telegramUserId).orElse(false))
                .map(r -> new View(r.getId(), r.getStatus(),
                        r.getAiResult() == null ? null : JsonMapper.shared().convertValue(r.getAiResult(), AdvisorDiagnosis.class)));
    }
}
