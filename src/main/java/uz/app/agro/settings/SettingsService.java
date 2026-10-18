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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return parseBool(raw(s));
    }

    public String getString(Setting s) {
        return raw(s);
    }

    public LocalDate getDate(Setting s) {
        try {
            return LocalDate.parse(raw(s));
        } catch (DateTimeParseException e) {
            return LocalDate.parse(s.defaultValue());          // DB'da qo'lda buzilgan — default
        }
    }

    /** Bir nechta BOOL sozlamadan yoqilganlari — bitta SELECT (bot menyusi har xabarda yasaladi) */
    public Set<Setting> enabled(Collection<Setting> flags) {
        Map<String, String> saved = new HashMap<>();
        for (var e : repository.findAllById(flags.stream().map(Setting::key).toList())) saved.put(e.getKey(), e.getValue());
        Set<Setting> on = EnumSet.noneOf(Setting.class);
        for (Setting f : flags) if (parseBool(saved.getOrDefault(f.key(), f.defaultValue()))) on.add(f);
        return on;
    }

    /** getBool va enabled — bir xil qoida (menyu va tugma gate'i farqlanmasin) */
    private static boolean parseBool(String raw) {
        return Boolean.parseBoolean(raw);
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
                // "Narx kelishiladi" (Agro Biznes, maydon cheksiz) — mukofot yoki sinov sifatida bepul berilmaydi
                if (p.isContactSales()) throw new BadRequestException("Narxi kelishiladigan tarifni bu yerda tanlab bo'lmaydi: " + code);
                yield code;
            }
            case DATE -> {
                try {
                    LocalDate d = LocalDate.parse(v);
                    if (d.getYear() < 2020 || d.getYear() > 2100) throw new BadRequestException(s.key() + ": yil 2020–2100");
                    yield d.toString();
                } catch (DateTimeParseException e) {
                    throw new BadRequestException(s.key() + ": sana YYYY-MM-DD (masalan 2027-10-31)");
                }
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
        if (s == Setting.SEASON_SALE_FROM || s == Setting.SEASON_END) return seasonWarning();
        if (s.type() != Setting.Type.PLAN) return null;
        // Optional.map ichidan null qaytarib bo'lmaydi (bo'sh Optional → "topilmadi") — oddiy if
        PlanEntity p = planRepository.findByCode(value).orElse(null);
        if (p == null) return "Tarif topilmadi: " + value;
        if (p.isSystemPlan() || !p.isActive()) {
            return "«" + p.getName() + "» tarifi faol emas — bu xizmat ishlamaydi. Tariflar sahifasida faollashtiring yoki boshqa tarif tanlang";
        }
        if (s == Setting.SEASON_PLAN && p.getPrice().signum() <= 0) {
            return "«" + p.getName() + "» tarifining narxi 0 — mavsumiy taklif koʻrinmaydi";
        }
        return null;
    }

    /** Mavsum sanalari jimgina taklifni yashirib qo'ymasin — admin sababini ko'rsin */
    private String seasonWarning() {
        LocalDate from = getDate(Setting.SEASON_SALE_FROM), end = getDate(Setting.SEASON_END);
        if (!from.isBefore(end)) return "Sotuv boshlanishi mavsum oxiridan oldin boʻlsin — hozir taklif koʻrinmaydi";
        if (!end.isAfter(LocalDate.now())) return "Mavsum oxiri oʻtib ketgan — taklif koʻrinmaydi";
        return null;
    }
}
