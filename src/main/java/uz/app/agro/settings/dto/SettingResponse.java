package uz.app.agro.settings.dto;

import java.time.LocalDateTime;

/** value — joriy (DB yoki default); custom — admin o'zgartirganmi; warning — qiymat hozir ishlamaydi (masalan tarif o'chirilgan) */
public record SettingResponse(
        String key, String group, String description, String type, String value, String defaultValue,
        Integer min, Integer max, boolean custom, String updatedByEmail, LocalDateTime updatedDate,
        String warning
) {}
