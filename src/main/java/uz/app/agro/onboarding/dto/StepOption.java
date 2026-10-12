package uz.app.agro.onboarding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/** value — saqlanadigan qiymat (callback_data'ga tushadi — qisqa, lotin); labels — tugma matni tillar bo'yicha */
public record StepOption(
        @NotNull @Pattern(regexp = "[A-Z0-9_]{1,24}", message = "variant qiymati: A-Z, 0-9, _ (1–24)") String value,
        @NotNull Map<String, String> labels
) {}
