package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Navbat: analysis_jobs. Bir nechta instans xavfsiz — FOR UPDATE SKIP LOCKED.
 * fixedDelay (fixedRate emas): oldingi paket tugamaguncha keyingisi boshlanmaydi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "analysis.worker.enabled", havingValue = "true", matchIfMissing = true)
class AnalysisWorker {

    private final AnalysisJobStore store;
    private final AnalysisExecutor executor;

    @Scheduled(fixedDelayString = "${analysis.worker.poll-interval:10s}", initialDelayString = "${analysis.worker.initial-delay:15s}")
    public void poll() {
        try {
            int requeued = store.requeueStuck();
            if (requeued > 0) log.warn("RUNNING'da qotgan {} ta job navbatga qaytarildi", requeued);

            List<Long> ids = store.claimBatch();
            for (Long id : ids) {
                // Scheduler thread'i qayta ishlatiladi — MDC tozalanmasa keyingi job'ga yopishadi (CLAUDE.md 5.6)
                try {
                    MDC.put("requestId", "job-" + id);
                    executor.run(id);
                } finally {
                    MDC.clear();
                }
            }
        } catch (RuntimeException e) {
            // DB uzilsa ham scheduler to'xtamasin — keyingi poll qayta urinadi
            log.error("analysis worker poll xatosi", e);
        }
    }
}
