package uz.app.agro.farmer;

import uz.app.agro.field.Crop;

// Bot uchun: entity tranzaksiyadan tashqariga chiqmasin (LAZY, dirty checking)
public record FarmerSnapshot(
        Long id,
        long telegramUserId,
        String language,
        Crop mainCrop,
        IrrigationType irrigationType,
        WaterSource waterSource,
        boolean created
) {
    /** 3 ta boshlang'ich savolga javob bergan — /start ularni qayta so'ramaydi */
    public boolean onboarded() {
        return mainCrop != null && irrigationType != null && (waterSource != null || !needsWaterSource());
    }

    /** Lalmi sug'orilmaydi — suv manbai so'ralmaydi */
    public boolean needsWaterSource() {
        return irrigationType != IrrigationType.RAINFED;
    }

    static FarmerSnapshot of(FarmerEntity f, boolean created) {
        return new FarmerSnapshot(f.getId(), f.getTelegramUserId(), f.getLanguage(),
                f.getMainCrop(), f.getIrrigationType(), f.getWaterSource(), created);
    }
}
