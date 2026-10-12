package uz.app.agro.irrigation;

import java.time.LocalDate;

/** Dala sug'orish holati — bot va admin uchun (tranzaksiyadan tashqarida, entity emas) */
public record WaterStatus(
        long fieldId,
        String fieldName,
        long farmerTelegramUserId,
        long chatId,
        String language,
        boolean configured,             // oxirgi sug'orish ma'lum
        boolean enabled,
        SoilType soil,                  // fermer javobi (null — bilmaydi)
        SoilType soilAuto,              // SoilGrids
        SoilType soilUsed,
        LocalDate lastIrrigatedOn,
        LocalDate localToday,
        LocalDate nextIrrigationOn,
        Integer daysUntil,              // null — 7 kunlik prognozda kerak emas
        boolean due,
        Double depletionPct,
        Double rainNext3dMm,
        Double ndvi
) {
    /** Dalada ekin ko'rinmaydi (shudgor, qish) — eslatma yuborilmaydi */
    public boolean noCrop() {
        return ndvi != null && ndvi < 0.2;
    }

    public boolean soilMismatch() {
        return soil != null && soilAuto != null && soil != soilAuto;
    }
}
