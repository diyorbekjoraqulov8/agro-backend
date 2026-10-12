package uz.app.agro.farmer.dto;

import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.farmer.LeadStatus;
import uz.app.agro.farmer.WaterSource;
import uz.app.agro.field.Crop;

import java.time.LocalDateTime;

public record FarmerListItem(
        Long id,
        String fullName,
        String telegramUsername,
        String phone,
        String countryCode,
        String language,
        Crop mainCrop,
        IrrigationType irrigationType,
        WaterSource waterSource,
        String source,
        LeadStatus leadStatus,
        boolean blockedBot,
        LocalDateTime createdDate
) {}
