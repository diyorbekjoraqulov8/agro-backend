package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.satellite.SatelliteRejectedException;
import uz.app.agro.satellite.SatelliteUnavailableException;
import uz.app.agro.satellite.dto.AnalyzeRequest;
import uz.app.agro.satellite.dto.AnalyzeResponse;

import java.util.Optional;

/**
 * Bitta job: o'qish → Python → saqlash. Fermerga xabar — store tranzaksiyasida outbox'ga (NotificationService),
 * yuborish — NotificationDispatcher (bot o'chiq bo'lsa ham natija yo'qolmaydi, bot yoqilganda boradi).
 * Bu klassda @Transactional YO'Q — Python chaqiruvi 30–90 s, DB ulanishi band bo'lmasin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AnalysisExecutor {

    private final AnalysisJobStore store;
    private final SatelliteClient satellite;
    private final AnalysisImages images;

    void run(long jobId) {
        Optional<JobInput> loaded = store.loadInput(jobId);
        if (loaded.isEmpty()) {
            log.warn("job {}: dala o'chirilgan — FAILED", jobId);
            return;
        }
        JobInput in = loaded.get();
        long t0 = System.currentTimeMillis();
        try {
            AnalyzeResponse r = satellite.analyze(new AnalyzeRequest(in.fieldId(), in.geometry(), in.crop().name(),
                    in.seasonStart(), in.seasonEnd(), true));
            // Rasmlar diskka — tranzaksiyadan OLDIN (disk yozuvi DB ulanishini band qilmasin)
            store.saveSuccess(jobId, r, images.store(jobId, r.mapPng(), r.chartPng()));
            log.info("job {} DONE {} ms: {} ga, {} sahna, {}", jobId, System.currentTimeMillis() - t0,
                    r.areaHa(), r.scenesUsed(), r.countryCode());
        } catch (SatelliteRejectedException e) {
            store.saveRejected(jobId, e.getCode());
            log.info("job {} REJECTED {} {}", jobId, e.getCode(), e.getParams());
        } catch (SatelliteUnavailableException e) {
            retry(in, e.getMessage());
        } catch (RuntimeException e) {
            // Kutilmagan xato (bug, shartnoma buzilgan) — cheksiz takrorlanmasin: urinishlar soni bilan cheklangan
            log.error("job {}: kutilmagan xato", jobId, e);
            retry(in, "INTERNAL: " + e);
        }
    }

    private void retry(JobInput in, String error) {
        boolean finalFailure = store.saveRetry(in.jobId(), error);
        log.warn("job {} {}: {}", in.jobId(), finalFailure ? "FAILED (urinishlar tugadi)" : "qayta uriniladi", error);
    }
}
