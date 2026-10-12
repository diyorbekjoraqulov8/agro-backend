package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Eski tahlil rasmlarini (analysis_images BYTEA) fayl servisiga ko'chiradi — ishga tushganda, fonda.
 * Har qator alohida: bittasi xato bersa qolganlari to'xtamaydi. Faylni saqlab bo'lmagan yangi tahlillar ham shu yerda.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AnalysisImageBackfill {

    private static final int BATCH = 50;

    private final AnalysisImageRepository repository;
    private final AnalysisImages images;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread.ofVirtual().name("analysis-image-backfill").start(this::run);
    }

    private void run() {
        int ok = 0, failed = 0;
        long after = 0;
        while (true) {
            List<Long> ids = repository.findLegacyIds(after, PageRequest.of(0, BATCH));
            if (ids.isEmpty()) break;
            after = ids.getLast();
            for (Long jobId : ids) {
                try {
                    AnalysisImage img = repository.findById(jobId).orElse(null);
                    if (img == null || img.getMapPng() == null || img.getChartPng() == null) {
                        failed++;
                        continue;
                    }
                    AnalysisImages.Stored s = images.store(jobId, img.getMapPng(), img.getChartPng());
                    if (s.ok() && repository.attachFiles(jobId, s.mapHashId(), s.chartHashId()) == 1) ok++;
                    else failed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("tahlil {} rasmlari ko'chirilmadi: {}", jobId, e.getMessage());
                }
            }
        }
        if (ok + failed > 0) log.info("eski tahlil rasmlari fayl servisiga ko'chirildi: {} ta, xato: {}", ok, failed);
    }
}
