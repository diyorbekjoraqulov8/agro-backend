package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.app.agro.problem.ProblemRecorder;
import uz.app.agro.problem.ProblemSource;
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
    private final ProblemRecorder problems;

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
            store.saveSuccess(jobId, r, images.store(jobId, r.mapPng(), r.chartPng()),
                    r.debug() != null ? images.storeNdvi(jobId, r.debug().ndviMapPng()) : null);
            log.info("job {} DONE {} ms: {} ga, {} sahna, {}", jobId, System.currentTimeMillis() - t0,
                    r.areaHa(), r.scenesUsed(), r.countryCode());
        } catch (SatelliteRejectedException e) {
            store.saveRejected(jobId, e.getCode());
            log.info("job {} REJECTED {} {}", jobId, e.getCode(), e.getParams());
            // Fermer/dala sababi (dala juda kichik, surat yo'q) — tizim xatosi emas, lekin ko'rinsin
            problems.warn(ProblemSource.ANALYSIS, "Tahlil rad etildi: " + e.getCode())
                    .field(in.fieldId()).ref("ANALYSIS", jobId).record();
        } catch (SatelliteUnavailableException e) {
            // Xizmat o'chiq ekani /health bilan tasdiqlanadi: tirik bo'lsa — aybdor shu job (urinish sanaladi)
            if (e.serviceDown() && !satellite.healthy() && store.saveWaiting(jobId, e.getMessage())) {
                log.warn("job {}: satellite ishlamayapti — urinish sanalmadi, kutamiz: {}", jobId, e.getMessage());
                problems.error(ProblemSource.SYSTEM, "Python tahlil xizmati (satellite) ishlamayapti — tahlillar kutmoqda")
                        .ref("ANALYSIS", jobId).cause(e).record();
            } else {
                retry(in, e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            // Kutilmagan xato (bug, shartnoma buzilgan) — cheksiz takrorlanmasin: urinishlar soni bilan cheklangan
            log.error("job {}: kutilmagan xato", jobId, e);
            retry(in, "INTERNAL: " + e, e);
        }
    }

    private void retry(JobInput in, String error, Throwable cause) {
        boolean finalFailure = store.saveRetry(in.jobId(), error);
        log.warn("job {} {}: {}", in.jobId(), finalFailure ? "FAILED (urinishlar tugadi)" : "qayta uriniladi", error);
        if (finalFailure) {
            problems.error(ProblemSource.ANALYSIS, "Tahlil bajarilmadi: " + error)
                    .field(in.fieldId()).ref("ANALYSIS", in.jobId()).cause(cause).record();
        }
    }
}
