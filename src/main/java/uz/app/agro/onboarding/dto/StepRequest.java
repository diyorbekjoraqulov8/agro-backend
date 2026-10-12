package uz.app.agro.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import uz.app.agro.onboarding.StepKind;

import java.util.List;
import java.util.Map;

/**
 * Yaratish: kind va code majburiy. Tahrirlash: code/kind o'zgarmaydi (e'tiborsiz); texts.uz majburiy;
 * options — faqat maxsus (target'siz) QUESTION uchun.
 */
public record StepRequest(
        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}", message = "kod: katta lotin harf bilan boshlansin, A-Z 0-9 _ (2–32)") String code,
        StepKind kind,
        @NotNull Map<String, String> texts,
        List<@Valid StepOption> options,
        Boolean active
) {}
