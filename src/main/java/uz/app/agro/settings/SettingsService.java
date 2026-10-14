package uz.app.agro.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.billing.PlanEntity;
import uz.app.agro.billing.PlanRepository;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.security.CurrentActor;
import uz.app.agro.settings.dto.SettingResponse;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sozlamalar. O'qish (int/bool/string) — @PreAuthorize YO'Q: bot va job'lar chaqiradi.
 * Ro'yxat va o'zgartirish — faqat xodim (settings:read / settings:manage).
 * Kesh yo'q: har chaqiruv — PK bo'yicha bitta SELECT; admin o'zgartirsa barcha instanslarda darhol kuchga kiradi.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsService {

    private final AppSettingRepository repository;
    private final PlanRepository planRepository;
    private final AuditService auditService;

    public int getInt(Setting s) {
        try {
            return Integer.parseInt(raw(s));
        } catch (NumberFormatException e) {
            return Integer.parseInt(s.defaultValue());        // DB'da qo'lda buzilgan — default
        }
    }

    public boolean getBool(Setting s) {
        return Boolean.parseBoolean(raw(s));
    }

    public String getString(Setting s) {
        return raw(s);
    }

    private String raw(Setting s) {
        return repository.findById(s.key()).map(AppSettingEntity::getValue).orElse(s.defaultValue());
    }

    @PreAuthorize(Permissions.CAN_READ_SETTINGS)
    public List<SettingResponse> list() {
        Map<String, AppSettingEntity> saved = repository.findAll().stream()
                .collect(Collectors.toMap(AppSettingEntity::getKey, Function.identity()));
        return Arrays.stream(Setting.values()).map(s -> toResponse(s, saved.get(s.key()))).toList();
    }

    @PreAuthorize(Permissions.CAN_MANAGE_SETTINGS)
    @Transactional
    public SettingResponse update(String key, String value) {
        Setting s = require(key);
        String v = normalize(s, value);
        AppSettingEntity e = repository.findById(key).orElseGet(() -> {
            AppSettingEntity n = new AppSettingEntity();
            n.setKey(key);
            return n;
        });
        String old = e.getValue() != null ? e.getValue() : s.defaultValue();
        e.setValue(v);
        e.setUpdatedDate(LocalDateTime.now());
        e.setUpdatedByEmail(CurrentActor.get().map(a -> a.email()).orElse(null));
        repository.save(e);
        auditService.event(AuditEvent.SETTING_CHANGED).target("SETTING", key)
                .detail("old", old).detail("new", v).record();
        return toResponse(s, e);
    }

    /** Default'ga qaytarish */
    @PreAuthorize(Permissions.CAN_MANAGE_SETTINGS)
    @Transactional
    public SettingResponse reset(String key) {
        Setting s = require(key);
        repository.findById(key).ifPresent(e -> {
            repository.delete(e);
            auditService.event(AuditEvent.SETTING_CHANGED).target("SETTING", key)
                    .detail("old", e.getValue()).detail("new", s.defaultValue() + " (default)").record();
        });
        return toResponse(s, null);
    }

    /** Turi bo'yicha tekshiruv — noto'g'ri qiymat bazaga tushmasin (job'lar uni o'qiganda yiqilmasin) */
    private String normalize(Setting s, String value) {
        String v = value == null ? "" : value.strip();
        return switch (s.type()) {
            case BOOL -> {
                if (!v.equals("true") && !v.equals("false")) throw new BadRequestException(s.key() + ": true yoki false");
                yield v;
            }
            case INT -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new BadRequestException(s.key() + ": butun son kiriting");
                }
                if ((s.min() != null && n < s.min()) || (s.max() != null && n > s.max())) {
                    throw new BadRequestException(s.key() + ": " + s.min() + " dan " + s.max() + " gacha");
                }
                yield String.valueOf(n);
            }
            case PLAN -> {
                String code = v.toUpperCase();
                PlanEntity p = planRepository.findByCode(code)
                        .orElseThrow(() -> new BadRequestException("Tarif topilmadi: " + code));
                if (p.isSystemPlan() || !p.isActive()) throw new BadRequestException("Tarif pullik va faol bo'lsin: " + code);
                yield code;
            }
            case STRING -> {
                if (v.isEmpty()) throw new BadRequestException(s.key() + ": bo'sh bo'lmasin");
                yield v;
            }
            // Model ID ro'yxat bilan cheklanmaydi — yangi model chiqsa deploysiz almashtirish uchun; faqat shakl
            case MODEL -> {
                if (!v.matches("claude-[a-z0-9.-]{3,70}")) {           // ≤ 77 — advisor_requests.ai_model VARCHAR(80)
                    throw new BadRequestException(s.key() + ": Claude model ID (masalan claude-opus-5-5, claude-haiku-5-5)");
                }
                yield v;
            }
        };
    }

    private static Setting require(String key) {
        Setting s = Setting.byKey(key);
        if (s == null) throw new NotFoundException("Sozlama", key);
        return s;
    }

    private SettingResponse toResponse(Setting s, AppSettingEntity e) {
        String value = e != null ? e.getValue() : s.defaultValue();
        return new SettingResponse(s.key(), s.group(), s.description(), s.type().name(),
                value, s.defaultValue(), s.min(), s.max(), e != null,
                e != null ? e.getUpdatedByEmail() : null, e != null ? e.getUpdatedDate() : null, warning(s, value));
    }

    /** Saqlanganda to'g'ri edi, keyin buzilgan bo'lishi mumkin (masalan tarif nofaol qilindi) — admin ko'rsin */
    private String warning(Setting s, String value) {
        if (s.type() != Setting.Type.PLAN) return null;
        // Optional.map ichidan null qaytarib bo'lmaydi (bo'sh Optional → "topilmadi") — oddiy if
        PlanEntity p = planRepository.findByCode(value).orElse(null);
        if (p == null) return "Tarif topilmadi: " + value;
        if (p.isSystemPlan() || !p.isActive()) {
            return "«" + p.getName() + "» tarifi faol emas — bu xizmat ishlamaydi. Tariflar sahifasida faollashtiring yoki boshqa tarif tanlang";
        }
        return null;
    }
}
