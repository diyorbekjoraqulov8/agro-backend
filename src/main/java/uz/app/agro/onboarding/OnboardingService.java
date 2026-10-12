package uz.app.agro.onboarding;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.farmer.FarmerRegistrationService;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.farmer.FarmerSnapshot;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.farmer.WaterSource;
import uz.app.agro.field.Crop;
import uz.app.agro.onboarding.dto.StepOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Onboarding oqimi — bot chaqiradi, @PreAuthorize YO'Q (xodim uchun — OnboardingAdminService).
 * Qadamlar bazada (admin o'zgartiradi): faol, tartib bo'yicha, FINISH — har doim oxirida.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final OnboardingStepRepository repository;
    private final FarmerRepository farmerRepository;
    private final FarmerRegistrationService farmers;

    /** Botga kerakli ko'rinish — entity tranzaksiyadan tashqariga chiqmaydi */
    public record Step(String code, StepKind kind, StepTarget target, Map<String, String> texts, List<StepOption> options) {

        public boolean question() {
            return kind == StepKind.QUESTION;
        }

        /** Tanlangan til bo'sh bo'lsa — o'zbekcha, u ham bo'lmasa — birinchisi */
        public String text(String lang) {
            String t = texts.get(lang);
            if (t == null || t.isBlank()) t = texts.get("uz");
            if (t == null || t.isBlank()) t = texts.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
            return t;
        }

        /** Saqlanadigan qiymatlar: o'rnatilgan savolda — enum, maxsusda — admin variantlari */
        public List<String> values() {
            if (target == null) return options.stream().map(StepOption::value).toList();
            return Arrays.stream(switch (target) {
                case MAIN_CROP -> Crop.values();
                case IRRIGATION_TYPE -> IrrigationType.values();
                case WATER_SOURCE -> WaterSource.values();
            }).map(Enum::name).toList();
        }
    }

    public List<Step> flow() {
        List<Step> out = new ArrayList<>();
        Step finish = null;
        for (OnboardingStepEntity e : repository.findAllByOrderByPositionAscIdAsc()) {
            if (!e.isActive()) continue;
            Step s = toStep(e);
            if (OnboardingStepEntity.FINISH.equals(e.getCode())) finish = s;
            else out.add(s);
        }
        if (finish != null) out.add(finish);
        return out;
    }

    public List<Step> questions() {
        return flow().stream().filter(Step::question).toList();
    }

    public Optional<Step> step(String code) {
        return repository.findByCode(code).filter(OnboardingStepEntity::isActive).map(OnboardingService::toStep);
    }

    /** Maxsus savollarga javoblar (kod → qiymat) */
    public Map<String, String> answers(long telegramUserId) {
        return farmerRepository.findByTelegramUserId(telegramUserId)
                .map(f -> repository.answers(f.getId()).stream()
                        .collect(Collectors.toMap(OnboardingStepRepository.AnswerRow::getCode, OnboardingStepRepository.AnswerRow::getValue)))
                .orElse(Map.of());
    }

    public boolean answered(Step s, FarmerSnapshot f, Map<String, String> answers) {
        if (s.target() == null) return answers.containsKey(s.code());
        return switch (s.target()) {
            case MAIN_CROP -> f.mainCrop() != null;
            case IRRIGATION_TYPE -> f.irrigationType() != null;
            case WATER_SOURCE -> f.waterSource() != null;
        };
    }

    /**
     * Javob: qiymat shu savolning variantlaridan biri bo'lishi SHART (callback_data — ishonchsiz kirish).
     * @return false — noma'lum savol/qiymat (jim e'tiborsiz)
     */
    @Transactional
    public boolean answer(long telegramUserId, String code, String value) {
        Step s = step(code).filter(Step::question).orElse(null);
        if (s == null || value == null || !s.values().contains(value)) return false;
        if (s.target() != null) {
            switch (s.target()) {
                case MAIN_CROP -> farmers.setCrop(telegramUserId, Crop.valueOf(value));
                case IRRIGATION_TYPE -> farmers.setIrrigation(telegramUserId, IrrigationType.valueOf(value));
                case WATER_SOURCE -> farmers.setWaterSource(telegramUserId, WaterSource.valueOf(value));
            }
            return true;
        }
        return farmerRepository.findByTelegramUserId(telegramUserId).map(f -> {
            repository.saveAnswer(f.getId(), code, value);
            return true;
        }).orElse(false);
    }

    @SuppressWarnings("unchecked")
    static Step toStep(OnboardingStepEntity e) {
        List<StepOption> options = e.getOptions() == null ? List.of() : e.getOptions().stream()
                .map(o -> new StepOption(String.valueOf(o.get("value")),
                        o.get("labels") instanceof Map<?, ?> m ? (Map<String, String>) m : Map.of()))
                .toList();
        // Map.copyOf null qiymatda NPE — JSON'da bo'sh til bo'lishi mumkin
        return new Step(e.getCode(), e.getKind(), e.getTarget(), new java.util.HashMap<>(e.getTexts()), options);
    }
}
