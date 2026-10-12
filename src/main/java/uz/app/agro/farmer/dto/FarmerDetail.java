package uz.app.agro.farmer.dto;

import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.farmer.LeadStatus;
import uz.app.agro.farmer.WaterSource;
import uz.app.agro.field.Crop;
import uz.app.agro.field.dto.FieldSummary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FarmerDetail(
        Long id,
        Long telegramUserId,
        String fullName,
        String telegramUsername,
        String phone,
        String countryCode,
        String language,
        Crop mainCrop,
        IrrigationType irrigationType,
        WaterSource waterSource,
        BigDecimal totalAreaHa,
        String source,
        LeadStatus leadStatus,
        String leadNote,
        boolean blockedBot,
        LocalDateTime createdDate,
        LocalDateTime updatedDate,
        List<FieldSummary> fields,
        EffectivePlan plan                  // bugungi tarif va limitlar
) {}
