package uz.app.agro.monitoring;

import uz.app.agro.satellite.dto.WaterDtos.MonitorResponse;

/**
 * Yangi surat natijasi. Fermerga xabar — faqat DECLINE: dala o'sayotgan edi va uning sezilarli qismi sustlashdi.
 * O'rim-yig'im (butun dala yalang'och bo'ldi) va ekinsiz dala — muammo emas, xabar yuborilmaydi.
 */
public enum Verdict {
    DECLINE, STABLE, HARVEST, NO_CROP, BASELINE;

    /** Oldingi suratda shundan kam — dalada o'sayotgan ekin yo'q edi (shudgor, qish) */
    static final double CROP_NDVI = 0.25;
    /** Yangi suratda shundan kam va deyarli butun dala pasaygan — yig'ib olingan yoki haydalgan */
    static final double BARE_NDVI = 0.20;
    static final double HARVEST_DECLINE_PCT = 70;

    public static Verdict of(MonitorResponse r, int declinePct) {
        if (r.refDate() == null || r.ndviRef() == null || r.declinePct() == null) return BASELINE;
        if (r.ndviRef() < CROP_NDVI) return NO_CROP;
        if (r.ndvi() != null && r.ndvi() < BARE_NDVI && r.declinePct() >= HARVEST_DECLINE_PCT) return HARVEST;
        return r.declinePct() >= declinePct ? DECLINE : STABLE;
    }
}
