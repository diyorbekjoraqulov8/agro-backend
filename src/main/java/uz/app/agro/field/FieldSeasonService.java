package uz.app.agro.field;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.field.FieldSeasonEntity.Sowing;
import uz.app.agro.field.FieldSeasonEntity.Status;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Ekin mavsumi (H1): ekish sanasi, sug'orish usuli, "🌾 Hosilni yig'dim".
 * Bot metodlari telegramUserId bilan — callback_data ishonchsiz, begona dala "topilmadi" (ADR #16).
 * @PreAuthorize YO'Q — controller'ga ULANMASIN (admin — FieldService orqali o'qiydi).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldSeasonService {

    /** Ekish sanasi bundan eski bo'lmaydi — ko'p yillik ekin (beda, bog') ham joriy mavsumdan hisoblanadi */
    static final int MAX_AGE_DAYS = 400;

    private final FieldRepository fieldRepository;
    private final FieldSeasonRepository seasonRepository;
    private final AuditService auditService;

    public record SeasonView(long fieldId, String fieldName, Crop crop, Sowing sowing, LocalDate sownOn, boolean approx,
                             IrrigationType irrigation) {}

    public Optional<SeasonView> view(long telegramUserId, long fieldId) {
        return owned(telegramUserId, fieldId).map(f -> view(f, seasonRepository
                .findByFieldIdAndStatus(fieldId, Status.ACTIVE).orElse(null)));
    }

    /** Yangi dala — sanasi so'ralmagan faol mavsum. Dala yozuvi bilan atomik (FieldIntakeService.submit) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void start(long fieldId, Crop crop) {
        FieldSeasonEntity s = new FieldSeasonEntity();
        s.setFieldId(fieldId);
        s.setCrop(crop);
        seasonRepository.save(s);
    }

    /**
     * Ekin almashtirildi (FieldBotService.setCrop). Ekilgan mavsum — yopiladi va yangi ochiladi (yangi ekin = yangi ekish);
     * hali ekilmagan — faqat ekini o'zgaradi. @return ekish sanasini qayta so'rash kerakmi
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cropChanged(long fieldId, Crop crop) {
        fieldRepository.findLockedById(fieldId);
        FieldSeasonEntity s = active(fieldId, crop);
        if (s.getSowing() == Sowing.SOWN) {
            close(s);
            start(fieldId, crop);
            return true;
        }
        s.setCrop(crop);
        return s.getSowing() != Sowing.NOT_SOWN;
    }

    /**
     * @param sownOn faqat SOWN da (aks holda e'tiborsiz); kelajakda yoki MAX_AGE_DAYS dan eski — rad (bo'sh)
     */
    @Transactional
    public Optional<SeasonView> setSowing(long telegramUserId, long fieldId, Sowing sowing, LocalDate sownOn, boolean approx) {
        if (sowing == Sowing.UNANSWERED) return Optional.empty();
        LocalDate today = LocalDate.now();
        if (sowing == Sowing.SOWN && (sownOn == null || sownOn.isAfter(today) || sownOn.isBefore(today.minusDays(MAX_AGE_DAYS)))) {
            return Optional.empty();
        }
        return ownedLocked(telegramUserId, fieldId).map(f -> {
            FieldSeasonEntity s = active(fieldId, f.getCrop());
            s.setSowing(sowing);
            s.setSownOn(sowing == Sowing.SOWN ? sownOn : null);
            s.setSownOnApprox(sowing == Sowing.SOWN && approx);
            auditService.event(AuditEvent.FIELD_UPDATED).target("FIELD", fieldId)
                    .detail("sowing", sowing + (s.getSownOn() != null ? " " + s.getSownOn() + (approx ? " ~" : "") : ""))
                    .record();
            return view(f, s);
        });
    }

    @Transactional
    public Optional<SeasonView> setIrrigation(long telegramUserId, long fieldId, IrrigationType irrigation) {
        return ownedLocked(telegramUserId, fieldId).map(f -> {
            IrrigationType old = f.getIrrigationMethod();
            if (old != irrigation) {
                f.setIrrigationMethod(irrigation);
                auditService.event(AuditEvent.FIELD_UPDATED).target("FIELD", fieldId)
                        .detail("irrigation", old + " → " + irrigation).record();
            }
            return view(f, active(fieldId, f.getCrop()));
        });
    }

    /**
     * "🌾 Hosilni yig'dim": mavsum yopiladi, shu ekin bilan yangisi ochiladi (keyingi ekinni fermer o'zgartiradi).
     * Faqat ekilgan (SOWN) mavsum — callback ishonchsiz; ikkinchi bosish yangi (UNANSWERED) mavsumni ko'radi — bo'sh
     */
    @Transactional
    public Optional<SeasonView> harvest(long telegramUserId, long fieldId) {
        return ownedLocked(telegramUserId, fieldId).flatMap(f -> {
            FieldSeasonEntity s = active(fieldId, f.getCrop());
            if (s.getSowing() != Sowing.SOWN) return Optional.empty();
            close(s);
            start(fieldId, f.getCrop());
            auditService.event(AuditEvent.FIELD_UPDATED).target("FIELD", fieldId)
                    .detail("harvest", s.getCrop() + " " + s.getSownOn() + ".." + s.getFinishedOn())
                    .record();
            return Optional.of(view(f, null));
        });
    }

    /** Faol mavsum — faqat dala qulflangandan keyin (ownedLocked / cropChanged); yo'q bo'lsa (nomuvofiq holat) — ochiladi */
    private FieldSeasonEntity active(long fieldId, Crop crop) {
        return seasonRepository.findByFieldIdAndStatus(fieldId, Status.ACTIVE).orElseGet(() -> {
            FieldSeasonEntity s = new FieldSeasonEntity();
            s.setFieldId(fieldId);
            s.setCrop(crop);
            return seasonRepository.save(s);
        });
    }

    private void close(FieldSeasonEntity s) {
        s.setStatus(Status.FINISHED);
        s.setFinishedOn(LocalDate.now());
        // Qisman unique index: yangi ACTIVE INSERT'idan OLDIN UPDATE bazaga tushsin (Hibernate INSERT'ni oldin yuboradi)
        seasonRepository.saveAndFlush(s);
    }

    /** Yozish yo'llari: dala qatori qulflanadi — bir dala mavsumi bo'yicha parallel so'rovlar ketma-ket bajariladi */
    private Optional<FieldEntity> ownedLocked(long telegramUserId, long fieldId) {
        return fieldRepository.findLockedById(fieldId).filter(f -> f.getFarmer().getTelegramUserId() == telegramUserId);
    }

    private Optional<FieldEntity> owned(long telegramUserId, long fieldId) {
        return fieldRepository.findById(fieldId).filter(f -> f.getFarmer().getTelegramUserId() == telegramUserId);
    }

    private static SeasonView view(FieldEntity f, FieldSeasonEntity s) {
        return new SeasonView(f.getId(), f.getName(), s != null ? s.getCrop() : f.getCrop(),
                s != null ? s.getSowing() : Sowing.UNANSWERED, s != null ? s.getSownOn() : null,
                s != null && s.isSownOnApprox(), f.getIrrigationMethod());
    }
}
