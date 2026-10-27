package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.file.FileRegistry;

/**
 * Tahlil rasmlari ↔ fayl servisi. Saqlash — tranzaksiyadan TASHQARIDA (disk), o'qish — fayldan yoki eski BYTEA'dan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AnalysisImages {

    private final FileRegistry files;

    record Stored(String mapHashId, String chartHashId) {
        static final Stored NONE = new Stored(null, null);
        boolean ok() { return mapHashId != null && chartHashId != null; }
    }

    /** Saqlab bo'lmasa — NONE: chaqiruvchi BYTEA'ga yozadi (tahlil natijasi yo'qolmasin), fonda keyin ko'chadi */
    Stored store(long jobId, byte[] mapPng, byte[] chartPng) {
        try {
            String map = files.store(mapPng, "tahlil-" + jobId + "-xarita.png", FileRegistry.Uploader.system()).hashId();
            String chart = files.store(chartPng, "tahlil-" + jobId + "-grafik.png", FileRegistry.Uploader.system()).hashId();
            return new Stored(map, chart);
        } catch (RuntimeException e) {
            log.warn("tahlil {} rasmlari faylga saqlanmadi (BYTEA'ga yoziladi): {}", jobId, e.getMessage());
            return Stored.NONE;
        }
    }

    /** Debug xaritasi — ixtiyoriy: saqlanmasa null, tahlil baribir DONE (BYTEA zaxirasi yo'q) */
    String storeNdvi(long jobId, byte[] png) {
        if (png == null) return null;
        try {
            return files.store(png, "tahlil-" + jobId + "-ndvi.png", FileRegistry.Uploader.system()).hashId();
        } catch (RuntimeException e) {
            log.warn("tahlil {} NDVI xaritasi saqlanmadi: {}", jobId, e.getMessage());
            return null;
        }
    }

    byte[] ndvi(AnalysisImage img) {
        return read(img.getNdviFileHashId(), null, img.getJobId());
    }

    byte[] map(AnalysisImage img) {
        return read(img.getMapFileHashId(), img.getMapPng(), img.getJobId());
    }

    byte[] chart(AnalysisImage img) {
        return read(img.getChartFileHashId(), img.getChartPng(), img.getJobId());
    }

    private byte[] read(String hashId, byte[] legacy, Long jobId) {
        if (hashId != null) return files.content(hashId).bytes();
        if (legacy != null) return legacy;
        throw new NotFoundException("Tahlil rasmi", jobId);
    }
}
