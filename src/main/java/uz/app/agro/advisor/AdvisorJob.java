package uz.app.agro.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

/**
 * Navbatdagi rasmlarni Claude'ga yuboradi (har 3 s, tick'da ko'pi bilan 2 ta, bittadan olinadi — har biri ~10–40 s).
 * Bot thread'ini to'smaydi: fermer "tahlil qilinmoqda" deb darhol javob oladi, natija — bildirishnoma orqali.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "advisor.job-enabled", havingValue = "true", matchIfMissing = true)
class AdvisorJob {

    private static final int BATCH = 2;

    private final AdvisorStore store;
    private final ClaudeAdvisor claude;
    private final FileRegistry files;
    private final SettingsService settings;
    private final AdvisorProperties props;

    @Scheduled(fixedDelayString = "${advisor.interval:3s}", initialDelayString = "${advisor.initial-delay:15s}")
    public void tick() {
        try {
            for (int i = 0; i < BATCH; i++) {
                var id = store.claimNext();
                if (id.isEmpty()) break;
                process(id.get());
            }
        } catch (RuntimeException e) {
            log.error("advisor job'i to'xtadi", e);
        } finally {
            MDC.clear();
        }
    }

    private void process(long id) {
        MDC.put("requestId", "advisor-" + id);
        try {
            var in = store.input(id).orElse(null);
            if (in == null) return;
            var content = files.content(in.photoHashId());
            boolean png = "image/png".equals(content.info().contentType());
            long t0 = System.currentTimeMillis();
            var result = claude.diagnose(settings.getString(Setting.ADVISOR_MODEL), content.bytes(), png, in.context());
            if (!store.answered(id, result)) {
                log.warn("advisor {}: javob keldi, lekin so'rov endi PENDING emas — natija saqlanmadi", id);
                return;
            }
            log.info("advisor {}: {} ({} ms, {} + {} token, {})", id, result.diagnosis().category(),
                    System.currentTimeMillis() - t0, result.inputTokens(), result.outputTokens(), result.model());
        } catch (ClaudeAdvisor.AdvisorException e) {
            log.warn("advisor {}: {}", id, e.getMessage());
            store.failed(id, e.getMessage(), e.retryable(), props.maxAttempts());
        } catch (RuntimeException e) {
            log.warn("advisor {}: {}", id, e.toString());
            store.failed(id, e.toString(), false, props.maxAttempts());
        }
    }
}
