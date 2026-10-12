package uz.app.agro.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** value null emas; default'ga qaytarish — DELETE /settings/{key} */
public record SettingUpdateRequest(@NotNull(message = "value ko'rsatilishi shart") @Size(max = 200) String value) {}
