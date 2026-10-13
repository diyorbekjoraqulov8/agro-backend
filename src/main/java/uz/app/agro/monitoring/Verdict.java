package uz.app.agro.monitoring;

import uz.app.agro.satellite.dto.WaterDtos.MonitorResponse;

/**
 * Yangi surat natijasi. Fermerga xabar — faqat DECLINE: dala o'sayotgan edi va uning BIR QISMI sezilarli sustlashdi.
 * Butun dala bir tekis pasaysa — pishish (bug'doy), defoliatsiya (paxta), o'rim (beda) yoki yig'im: tabiiy, xabar yo'q.
 * Haqiqiy muammo (suv yetmaydi, zararkunanda) odatda dalaning bir qismida bo'ladi.
 */
public enum Verdict {
    DECLINE, STABLE, HARVEST, NO_CROP, BASELINE;

    /** Oldingi suratda shundan kam — dalada o'sayotgan ekin yo'q edi (shudgor, qish) */
    static final double CROP_NDVI = 0.25;
    /** Dalaning shuncha foizi pasaysa — bir tekis (tabiiy) jarayon, "muammo" emas */
    static final double UNIFORM_DECLINE_PCT = 70;
    /** Shundan eski surat bo'yicha "yangi surat" deb xabar yuborilmaydi (birinchi tekshiruvda 45 kunlik oynadan kelishi mumkin) */
    static final int MAX_SCENE_AGE_DAYS = 7;

    /**
     * @param firstCheck — dala endi kuzatila boshladi: oldingi holatni bilmaymiz, natija faqat boshlang'ich nuqta
     * @param sceneAgeDays — surat necha kun oldin olingan
     */
    public static Verdict of(MonitorResponse r, int declinePct, boolean firstCheck, long sceneAgeDays) {
        if (firstCheck || sceneAgeDays > MAX_SCENE_AGE_DAYS) return BASELINE;
        if (r.refDate() == null || r.ndviRef() == null || r.declinePct() == null) return BASELINE;
        if (r.ndviRef() < CROP_NDVI) return NO_CROP;
        if (r.declinePct() >= UNIFORM_DECLINE_PCT) return HARVEST;
        return r.declinePct() >= declinePct ? DECLINE : STABLE;
    }
}
