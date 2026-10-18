package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.app.agro.problem.ProblemRecorder;
import uz.app.agro.problem.ProblemSource;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.satellite.SatelliteRejectedException;
import uz.app.agro.satellite.SatelliteUnavailableException;
import uz.app.agro.satellite.dto.WaterDtos.MonitorRequest;
import uz.app.agro.satellite.dto.WaterDtos.MonitorResponse;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    /** Olingan dala shuncha vaqt band (boshqa instans tanlamaydi); ilova yiqilsa — keyin navbatga qaytadi */
    private static final String LEASE = "1 hour";

    private final MonitoringStore store;
    private final SatelliteClient satellite;
    private final FileRegistry files;
    private final SettingsService settings;
    private final MonitoringProperties props;
    private final ProblemRecorder problems;

    @Scheduled(cron = "${monitoring.cron:0 40 * * * *}")
    public void tick() {
        int hour = LocalDateTime.now().getHour();
        if (hour < props.hoursFrom() || hour >= props.hoursTo()) return;
        if (!settings.getBool(Setting.MONITORING_ENABLED)) return;
        // Barcha dalalar kuzatiladi ("Hozirgi holat" kartasi uchun); paid-only — faqat ogohlantirish va tezlik pullikka
        boolean paidOnly = settings.getBool(Setting.MONITORING_PAID_ONLY);
        try {
            MDC.put("requestId", "monitor");
            int enrolled = store.enroll(false);
            if (enrolled > 0) log.info("monitoring: {} ta yangi dala navbatga qo'shildi", enrolled);
            run(store.claim(false, props.batch(), LEASE), paidOnly);
        } catch (RuntimeException e) {
            log.error("monitoring job'i to'xtadi", e);
            problems.error(ProblemSource.MONITORING, "Monitoring job'i to'xtadi: " + e).cause(e).record();
        } finally {
            MDC.clear();
        }
    }

    private void run(List<Long> due, boolean paidOnly) {
        double drop = settings.getInt(Setting.MONITORING_DROP) / 100.0;
        int declinePct = settings.getInt(Setting.MONITORING_DECLINE_PCT);
        int checked = 0, alerts = 0;
        for (int i = 0; i < due.size(); i++) {
            long fieldId = due.get(i);
            MDC.put("requestId", "monitor-" + fieldId);
            try {
                var in = store.input(fieldId).orElse(null);
                if (in == null) continue;
                boolean full = !paidOnly || in.paid();          // pullik (yoki hammaga ochiq) — xabar va tez-tez tekshiruv
                // Bitta chaqiruv: xarita (PNG) Python'da faqat sustlashuv chegaradan oshganda yasaladi
                MonitorResponse r = satellite.monitor(new MonitorRequest(in.geometry(), in.since(), drop, declinePct));
                checked++;
                if (!r.newScene() || r.sceneDate() == null) {
                    store.reschedule(fieldId, LocalDateTime.now().plus(full ? props.afterNoScene() : props.freeAfterNoScene()), r.latestSeen());
                    continue;
                }
                long age = ChronoUnit.DAYS.between(r.sceneDate(), LocalDate.now());
                Verdict verdict = Verdict.of(r, declinePct, in.since() == null, age);
                String mapHashId = null;
                if (full && verdict == Verdict.DECLINE && r.mapPngBase64() != null && store.isNewScene(fieldId, r.sceneDate())) {
                    mapHashId = files.store(Base64.getDecoder().decode(r.mapPngBase64()),
                            "monitoring-" + fieldId + "-" + r.sceneDate() + ".png", FileRegistry.Uploader.system()).hashId();
                }
                if (store.record(in, r, verdict, mapHashId,
                        LocalDateTime.now().plus(full ? props.afterNewScene() : props.freeAfterNewScene()), full)) alerts++;
                log.info("monitoring: dala {} surat {} — {} (sustlashgan {}%)", fieldId, r.sceneDate(), verdict, r.declinePct());
            } catch (SatelliteRejectedException e) {
                // Poligon yaroqsiz / surat yo'q — kamroq urinish (har xatoda oraliq uzayadi)
                log.warn("monitoring: dala {} rad etildi: {}", fieldId, e.getMessage());
                problems.warn(ProblemSource.MONITORING, "Monitoring: dala rad etildi: " + e.getMessage()).field(fieldId).record();
                store.fail(fieldId, props.afterNoScene());
            } catch (SatelliteUnavailableException e) {
                if (e.serviceDown()) {
                    // Python yoki Earth Search ishlamayapti — qolganlari ham bo'lmaydi; ular lease tugagach (1 soat) qaytadi
                    log.warn("monitoring: satellite ishlamayapti ({}), {} ta dala keyingi safar", e.getMessage(), due.size() - i - 1);
                    store.fail(fieldId, props.afterUnavailable());
                    problems.error(ProblemSource.SYSTEM, "Python tahlil xizmati (satellite) ishlamayapti — monitoring kutmoqda")
                            .cause(e).record();
                    break;
                }
                log.warn("monitoring: dala {} Python xatosi: {}", fieldId, e.getMessage());   // 500 — faqat shu dala
                problems.error(ProblemSource.MONITORING, "Monitoring: Python xatosi: " + e.getMessage()).field(fieldId).record();
                store.fail(fieldId, props.afterNoScene());
            } catch (RuntimeException e) {
                log.warn("monitoring: dala {} xato: {}", fieldId, e.toString());
                problems.error(ProblemSource.MONITORING, "Monitoring xatosi: " + e).field(fieldId).cause(e).record();
                store.fail(fieldId, props.afterNoScene());
            }
        }
        if (checked > 0) log.info("monitoring: {} ta dala tekshirildi, {} ta xabar", checked, alerts);
    }
}
