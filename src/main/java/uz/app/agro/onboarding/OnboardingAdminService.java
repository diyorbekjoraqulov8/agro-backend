package uz.app.agro.onboarding;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.onboarding.dto.StepOption;
import uz.app.agro.onboarding.dto.StepRequest;
import uz.app.agro.onboarding.dto.StepResponse;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.CurrentActor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin → Onboarding: qadamlarni ko'rish, tahrirlash, qo'shish, tartiblash.
 * O'rnatilgan (system) qadamlar o'chirilmaydi; o'rnatilgan savollar va FINISH o'chirib qo'yilmaydi
 * (aks holda fermer hech qachon "ro'yxatdan o'tgan" bo'lmaydi yoki xaritaga yo'l ko'rsatilmaydi).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingAdminService {

    static final Set<String> LANGS = Set.of("uz", "ru", "en");
    private static final int MAX_TEXT = 3500;           // Telegram xabari ≤ 4096; raqam/ism uchun joy
    private static final int MAX_LABEL = 40;            // tugma matni

    private final OnboardingStepRepository repository;
    private final AuditService auditService;
    private final EntityManager em;

    @PreAuthorize(Permissions.CAN_READ_ONBOARDING)
    public List<StepResponse> list() {
        Map<String, Long> counts = answerCounts();
        List<OnboardingStepEntity> all = repository.findAllByOrderByPositionAscIdAsc();
        // FINISH ro'yxatda ham oxirida (bot bilan bir xil tartib)
        List<OnboardingStepEntity> ordered = new ArrayList<>(all.stream().filter(e -> !isFinish(e)).toList());
        all.stream().filter(OnboardingAdminService::isFinish).forEach(ordered::add);
        return ordered.stream().map(e -> toResponse(e, counts.getOrDefault(e.getCode(), 0L))).toList();
    }

    @PreAuthorize(Permissions.CAN_MANAGE_ONBOARDING)
    @Transactional
    public StepResponse create(StepRequest req) {
        if (req.kind() == null) throw new BadRequestException("kind ko'rsatilishi shart (MESSAGE yoki QUESTION)");
        if (req.code() == null) throw new BadRequestException("code ko'rsatilishi shart");
        if (repository.existsByCode(req.code())) throw new ConflictException("Bu kod band: " + req.code());
        OnboardingStepEntity e = new OnboardingStepEntity();
        e.setCode(req.code());
        e.setKind(req.kind());
        e.setTexts(texts(req.texts()));
        e.setOptions(req.kind() == StepKind.QUESTION ? options(req.options()) : null);
        e.setActive(req.active() == null || req.active());
        // Yangi qadam — FINISH'dan oldin, oxirgisidan keyin
        e.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().filter(x -> !isFinish(x))
                .mapToInt(OnboardingStepEntity::getPosition).max().orElse(0) + 10);
        touch(e);
        repository.save(e);
        audit(e, "create");
        return toResponse(e, 0);
    }

    @PreAuthorize(Permissions.CAN_MANAGE_ONBOARDING)
    @Transactional
    public StepResponse update(Long id, StepRequest req) {
        OnboardingStepEntity e = require(id);
        e.setTexts(texts(req.texts()));
        if (e.getKind() == StepKind.QUESTION && e.getTarget() == null) e.setOptions(options(req.options()));
        if (req.active() != null && req.active() != e.isActive()) {
            if (!req.active() && e.isSystem() && (e.getTarget() != null || isFinish(e))) {
                throw new BadRequestException("Bu qadamni o'chirib qo'yib bo'lmaydi — u bot ishlashi uchun kerak");
            }
            e.setActive(req.active());
        }
        touch(e);
        audit(e, "update");
        return toResponse(e, answerCounts().getOrDefault(e.getCode(), 0L));
    }

    /** Qo'shni qadam bilan joy almashish (FINISH qo'zg'almaydi) */
    @PreAuthorize(Permissions.CAN_MANAGE_ONBOARDING)
    @Transactional
    public List<StepResponse> move(Long id, boolean up) {
        OnboardingStepEntity e = require(id);
        if (isFinish(e)) throw new BadRequestException("Yakuniy xabar doim oxirida turadi");
        List<OnboardingStepEntity> list = repository.findAllByOrderByPositionAscIdAsc().stream().filter(x -> !isFinish(x)).toList();
        int i = list.indexOf(e);
        int j = up ? i - 1 : i + 1;
        if (j < 0 || j >= list.size()) return list();
        // Pozitsiyalar takrorlanmasin — avval barchasini 10, 20, 30… qilib qayta raqamlaymiz, keyin almashtiramiz
        for (int k = 0; k < list.size(); k++) list.get(k).setPosition((k + 1) * 10);
        OnboardingStepEntity other = list.get(j);
        int p = e.getPosition();
        e.setPosition(other.getPosition());
        other.setPosition(p);
        touch(e);
        audit(e, up ? "move-up" : "move-down");
        return list();
    }

    @PreAuthorize(Permissions.CAN_MANAGE_ONBOARDING)
    @Transactional
    public void delete(Long id) {
        OnboardingStepEntity e = require(id);
        if (e.isSystem()) throw new BadRequestException("O'rnatilgan qadamni o'chirib bo'lmaydi — faqat matnini o'zgartirish mumkin");
        repository.delete(e);
        audit(e, "delete");
    }

    // ── Tekshiruv ──

    private static Map<String, String> texts(Map<String, String> in) {
        if (in == null) throw new BadRequestException("texts ko'rsatilishi shart");
        Map<String, String> out = new HashMap<>();
        for (var en : in.entrySet()) {
            if (!LANGS.contains(en.getKey())) throw new BadRequestException("Noma'lum til: " + en.getKey() + " (uz, ru, en)");
            String v = en.getValue() == null ? "" : en.getValue().strip();
            if (v.length() > MAX_TEXT) throw new BadRequestException(en.getKey() + ": matn ko'pi bilan " + MAX_TEXT + " belgi");
            if (!v.isEmpty()) out.put(en.getKey(), v);
        }
        if (!out.containsKey("uz")) throw new BadRequestException("O'zbekcha matn majburiy");
        return out;
    }

    private static List<Map<String, Object>> options(List<StepOption> in) {
        if (in == null || in.size() < 2 || in.size() > 12) throw new BadRequestException("Savolda 2 tadan 12 tagacha variant bo'lsin");
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (StepOption o : in) {
            if (!seen.add(o.value())) throw new BadRequestException("Variant qiymati takrorlangan: " + o.value());
            Map<String, String> labels = new HashMap<>();
            for (var en : (o.labels() == null ? Map.<String, String>of() : o.labels()).entrySet()) {
                if (!LANGS.contains(en.getKey())) throw new BadRequestException("Noma'lum til: " + en.getKey());
                String v = en.getValue() == null ? "" : en.getValue().strip();
                if (v.length() > MAX_LABEL) throw new BadRequestException("Tugma matni ko'pi bilan " + MAX_LABEL + " belgi");
                if (!v.isEmpty()) labels.put(en.getKey(), v);
            }
            if (!labels.containsKey("uz")) throw new BadRequestException("Har variantda o'zbekcha matn majburiy: " + o.value());
            out.add(Map.of("value", o.value(), "labels", labels));
        }
        return out;
    }

    // ── Yordamchilar ──

    /** Har savolga nechta fermer javob bergan (o'rnatilganlar — fermer ustunlaridan) */
    private Map<String, Long> answerCounts() {
        Map<String, Long> m = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT step_code, count(*) FROM farmer_answers GROUP BY step_code
                UNION ALL SELECT 'CROP', count(main_crop) FROM farmers WHERE deleted = FALSE
                UNION ALL SELECT 'IRRIGATION', count(irrigation_type) FROM farmers WHERE deleted = FALSE
                UNION ALL SELECT 'WATER_SOURCE', count(water_source) FROM farmers WHERE deleted = FALSE
                """).getResultList();
        for (Object[] r : rows) m.merge((String) r[0], ((Number) r[1]).longValue(), Long::sum);
        return m;
    }

    private OnboardingStepEntity require(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Onboarding qadami", id));
    }

    private static boolean isFinish(OnboardingStepEntity e) {
        return OnboardingStepEntity.FINISH.equals(e.getCode());
    }

    private static void touch(OnboardingStepEntity e) {
        e.setUpdatedDate(LocalDateTime.now());
        e.setUpdatedByEmail(CurrentActor.get().map(a -> a.email()).orElse(null));
    }

    private void audit(OnboardingStepEntity e, String action) {
        auditService.event(AuditEvent.ONBOARDING_CHANGED).target("ONBOARDING_STEP", e.getId())
                .detail("code", e.getCode()).detail("action", action).record();
    }

    private static StepResponse toResponse(OnboardingStepEntity e, long answers) {
        OnboardingService.Step s = OnboardingService.toStep(e);
        return new StepResponse(e.getId(), e.getCode(), e.getPosition(), e.getKind(), e.getTarget(), s.texts(), s.options(),
                e.isSystem(), e.isActive(), e.getUpdatedByEmail(), e.getUpdatedDate(), answers);
    }
}
