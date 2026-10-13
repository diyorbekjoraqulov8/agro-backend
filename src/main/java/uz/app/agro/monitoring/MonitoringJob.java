package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.satellite.SatelliteRejectedException;
import uz.app.agro.satellite.SatelliteUnavailableException;
import uz.app.agro.satellite.dto.WaterDtos.MonitorRequest;
import uz.app.agro.satellite.dto.WaterDtos.MonitorResponse;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Har soat (kunduzi): kuzatiladigan dalalardan vaqti kelganlari (batch tadan) Python /monitor ga.
 * Python avval faqat metadata'ni so'raydi — yangi surat bo'lmasa ~2 s, bo'lsa piksel bo'yicha solishtirish ~10 s.
 * Yangi surat — 3 kundan keyin qayta, yo'q — ertaga. Fermerga xabar — faqat sezilarli sustlashuvda (Verdict.DECLINE).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitoring.job-enabled", havingValue = "true", matchIfMissing = true)
class MonitoringJob {

    /** Sustlashuv bo'lmasa xarita (PNG) so'ralmaydi — Python ham, disk ham tejaladi */
    private static final double NO_MAP = 101;

    private final MonitoringStore store;
    private final FieldMonitoringRepository queue;
    private final SatelliteClient satellite;
    private final FileRegistry files;
    private final SettingsService settings;
    private final MonitoringProperties props;

    @Scheduled(cron = "${monitoring.cron:0 40 * * * *}")
    public void tick() {
        int hour = LocalDateTime.now().getHour();
        if (hour < props.hoursFrom() || hour >= props.hoursTo()) return;
        if (!settings.getBool(Setting.MONITORING_ENABLED)) return;
        boolean paidOnly = settings.getBool(Setting.MONITORING_PAID_ONLY);
        try {
            MDC.put("requestId", "monitor");
            int enrolled = store.enroll(paidOnly);
            if (enrolled > 0) log.info("monitoring: {} ta yangi dala navbatga qo'shildi", enrolled);
            run(queue.due(paidOnly, props.batch()));
        } catch (RuntimeException e) {
            log.error("monitoring job'i to'xtadi", e);
        } finally {
            MDC.clear();
        }
    }

    private void run(List<Long> due) {
        double drop = settings.getInt(Setting.MONITORING_DROP) / 100.0;
        int declinePct = settings.getInt(Setting.MONITORING_DECLINE_PCT);
        int checked = 0, alerts = 0;
        for (Long fieldId : due) {
            MDC.put("requestId", "monitor-" + fieldId);
            var in = store.input(fieldId).orElse(null);
            if (in == null) continue;
            try {
                // 1-bosqich arzon: xaritasiz. Sustlashuv chiqsa — xarita bilan qayta (kamdan-kam)
                MonitorResponse r = satellite.monitor(new MonitorRequest(in.geometry(), in.since(), drop, NO_MAP));
                checked++;
                if (!r.newScene() || r.sceneDate() == null) {
                    store.reschedule(fieldId, LocalDateTime.now().plus(props.afterNoScene()), false);
                    continue;
                }
                Verdict verdict = Verdict.of(r, declinePct);
                String mapHashId = null;
                if (verdict == Verdict.DECLINE) {
                    MonitorResponse withMap = satellite.monitor(new MonitorRequest(in.geometry(), null, drop, 0));
                    if (r.sceneDate().equals(withMap.sceneDate()) && withMap.mapPngBase64() != null) {
                        mapHashId = files.store(Base64.getDecoder().decode(withMap.mapPngBase64()),
                                "monitoring-" + fieldId + "-" + r.sceneDate() + ".png", FileRegistry.Uploader.system()).hashId();
                    }
                }
                if (store.record(in, r, verdict, mapHashId, LocalDateTime.now().plus(props.afterNewScene()))) alerts++;
                log.info("monitoring: dala {} surat {} — {} (sustlashgan {}%)", fieldId, r.sceneDate(), verdict, r.declinePct());
            } catch (SatelliteRejectedException e) {
                // Poligon yaroqsiz / surat yo'q — ertaga (har soat urinish befoyda)
                log.warn("monitoring: dala {} rad etildi: {}", fieldId, e.getMessage());
                store.reschedule(fieldId, LocalDateTime.now().plus(props.afterNoScene()), true);
            } catch (SatelliteUnavailableException e) {
                // Python yoki Earth Search ishlamayapti — qolganlari ham bo'lmaydi, keyingi tick'da
                log.warn("monitoring: satellite ishlamayapti ({}), {} ta dala keyingi safar", e.getMessage(), due.size() - checked);
                store.reschedule(fieldId, LocalDateTime.now().plus(props.afterUnavailable()), true);
                break;
            } catch (RuntimeException e) {
                log.warn("monitoring: dala {} xato: {}", fieldId, e.toString());
                store.reschedule(fieldId, LocalDateTime.now().plus(props.afterNoScene()), true);
            }
        }
        if (checked > 0) log.info("monitoring: {} ta dala tekshirildi, {} ta xabar", checked, alerts);
    }
}
