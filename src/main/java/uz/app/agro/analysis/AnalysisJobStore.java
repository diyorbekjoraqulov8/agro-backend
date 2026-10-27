package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.satellite.dto.AnalyzeResponse;

import uz.app.agro.notification.Notification;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationService;
import uz.app.agro.notification.Templates;
import uz.app.agro.referral.ReferralService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Worker'ning tranzaksiya chegaralari — ALOHIDA bean (ADR #24): executor `this` orqali
 * chaqirsa proxy chetlanadi va @Transactional ishlamaydi. Har metod QISQA tranzaksiya:
 * Python'ga 30–90 s'lik chaqiruv bu yerda EMAS (Hikari pool band bo'lib qolardi).
 */
@Component
@RequiredArgsConstructor
class AnalysisJobStore {

    private final AnalysisJobRepository jobRepository;
    private final AnalysisImageRepository imageRepository;
    private final ReferralService referrals;
    private final NotificationService notifications;
    private final AnalysisProperties props;
    private final AuditService auditService;

    /** Ilova RUNNING paytida o'chgan bo'lsa — job'lar navbatga qaytadi. */
    @Transactional
    public int requeueStuck() {
        return jobRepository.requeueStuck(LocalDateTime.now().minus(props.stuckAfter()));
    }

    /**
     * PENDING → RUNNING. FOR UPDATE SKIP LOCKED faqat tranzaksiya ICHIDA qulflaydi; commit'dan
     * keyin qulf yechiladi, lekin status RUNNING — boshqa instans uni olmaydi.
     */
    @Transactional
    public List<Long> claimBatch() {
        List<AnalysisJob> jobs = jobRepository.lockNextBatch(props.batchSize());
        LocalDateTime now = LocalDateTime.now();
        for (AnalysisJob j : jobs) {
            j.setStatus(AnalysisJob.Status.RUNNING);
            j.setStartedAt(now);
            j.setAttempts(j.getAttempts() + 1);
        }
        return jobs.stream().map(AnalysisJob::getId).toList();
    }

    /** @return empty — dala o'chirilgan (job FAILED qilinadi) */
    @Transactional
    public Optional<JobInput> loadInput(long jobId) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        FieldEntity field = fieldOf(job);
        if (field == null) {
            fail(job, "FIELD_DELETED");
            return Optional.empty();
        }
        // Ish haqiqatan shu yerda boshlanadi (paketdagi ikkinchi job birinchisini kutib turgan bo'lishi mumkin)
        job.setStartedAt(LocalDateTime.now());
        return Optional.of(new JobInput(job.getId(), job.getKind(), field.getId(), field.getName(),
                new HashMap<>(field.getBoundaryGeojson()), job.getCrop() != null ? job.getCrop() : field.getCrop(),
                job.getSeasonStart(), job.getSeasonEnd(),
                field.getFarmer().getId()));
    }

    @Transactional
    public void saveSuccess(long jobId, AnalyzeResponse r, AnalysisImages.Stored files, String ndviHashId) {   // public — aks holda @Transactional ishlamaydi
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(AnalysisJob.Status.DONE);
        job.setFinishedAt(LocalDateTime.now());
        job.setError(null);
        job.setResult(resultWithoutImages(r));

        AnalysisImage img = imageRepository.findById(jobId).orElseGet(AnalysisImage::new);
        img.setJobId(jobId);
        img.setNdviFileHashId(ndviHashId);
        if (files.ok()) {
            img.setMapFileHashId(files.mapHashId());
            img.setChartFileHashId(files.chartHashId());
            img.setMapPng(null);
            img.setChartPng(null);
        } else {                                     // zaxira: fayl saqlanmadi — BYTEA, fonda keyin ko'chadi
            img.setMapPng(r.mapPng());
            img.setChartPng(r.chartPng());
        }
        imageRepository.save(img);

        // Aniq maydon va markaz — Python (UTM) dan; Mini App hisobiga ishonilmaydi
        FieldEntity field = job.getField();
        field.setAreaHa(BigDecimal.valueOf(r.areaHa()).setScale(2, RoundingMode.HALF_UP));
        field.setCentroidLat(BigDecimal.valueOf(r.centroidLat()).setScale(6, RoundingMode.HALF_UP));
        field.setCentroidLon(BigDecimal.valueOf(r.centroidLon()).setScale(6, RoundingMode.HALF_UP));
        FarmerEntity farmer = field.getFarmer();
        referrals.markActivated(farmer);              // birinchi tahlil — taklif "faol" (mukofot — commit'dan keyin)
        if (farmer.getCountryCode() == null && r.countryCode() != null) {
            farmer.setCountryCode(r.countryCode());
        }

        auditService.event(AuditEvent.ANALYSIS_COMPLETED)
                .target("ANALYSIS", jobId)
                .detail("fieldId", field.getId())
                .detail("areaHa", r.areaHa())
                .detail("scenes", r.scenesUsed())
                .record();
        // Outbox: hisobot xabari tahlil holati bilan BIRGA commit bo'ladi (biri yo'qolmaydi)
        notifyFarmer(job, farmer, Notification.template(farmer.getId(), NotificationCategory.ANALYSIS, Templates.ANALYSIS_COMPLETED)
                .param("jobId", jobId).preview("Tahlil №" + jobId + " tayyor — «" + field.getName() + "»"));
    }

    /** STAFF tahlili — xodim ishga tushirgan, fermerga avtomatik yuborilmaydi */
    private void notifyFarmer(AnalysisJob job, FarmerEntity farmer, Notification n) {
        if (job.getKind() != AnalysisJob.Kind.STAFF && farmer != null) notifications.enqueue(n);
    }

    /** 422/404 — qayta urinish foydasiz. */
    @Transactional
    public void saveRejected(long jobId, String code) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        fail(job, "REJECTED:" + code);
        FieldEntity field = fieldOf(job);
        if (field != null) {
            notifyFarmer(job, field.getFarmer(), Notification.template(field.getFarmer().getId(), NotificationCategory.ANALYSIS,
                    Templates.ANALYSIS_REJECTED).param("jobId", jobId).param("code", code).preview("Tahlil №" + jobId + " rad etildi: " + code));
        }
    }

    /**
     * Tarmoq/5xx/kutilmagan xato. Urinishlar qolgan bo'lsa — PENDING, eksponensial kutish
     * (retry-base × 2^(urinish-1): 2, 4, 8 daqiqa ...).
     * @return true — urinishlar tugadi, job FAILED
     */
    @Transactional
    public boolean saveRetry(long jobId, String error) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        if (job.getAttempts() >= props.maxAttempts()) {
            fail(job, error);
            FieldEntity field = fieldOf(job);
            if (field != null) {
                notifyFarmer(job, field.getFarmer(), Notification.template(field.getFarmer().getId(), NotificationCategory.ANALYSIS,
                        Templates.ANALYSIS_FAILED).param("jobId", jobId).preview("Tahlil №" + jobId + " — texnik xato"));
            }
            return true;
        }
        job.setStatus(AnalysisJob.Status.PENDING);
        job.setError(truncate(error));
        job.setNextTryAt(LocalDateTime.now().plus(props.retryBase().multipliedBy(1L << (job.getAttempts() - 1))));
        return false;
    }

    /**
     * Python xizmati umuman ishlamayapti — fermer aybi ham, dala aybi ham emas: urinish qaytariladi, kutamiz.
     * Juda uzoq (service-down-max) davom etsa — oddiy saveRetry (cheksiz navbat bo'lmasin).
     * @return true — kutishga qo'yildi; false — chegara o'tdi, chaqiruvchi saveRetry qiladi
     */
    @Transactional
    public boolean saveWaiting(long jobId, String error) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        if (job.getCreatedDate() != null && job.getCreatedDate().plus(props.serviceDownMax()).isBefore(LocalDateTime.now())) {
            return false;
        }
        job.setStatus(AnalysisJob.Status.PENDING);
        job.setAttempts(Math.max(0, job.getAttempts() - 1));
        job.setError(truncate("SERVICE_DOWN: " + error));
        job.setNextTryAt(LocalDateTime.now().plus(props.serviceDownWait()));
        return true;
    }

    private void fail(AnalysisJob job, String error) {
        job.setStatus(AnalysisJob.Status.FAILED);
        job.setFinishedAt(LocalDateTime.now());
        job.setError(truncate(error));
        auditService.event(AuditEvent.ANALYSIS_FAILED)
                .target("ANALYSIS", job.getId())
                .detail("error", truncate(error))
                .detail("attempts", job.getAttempts())
                .record();
    }

    // Dala soft-delete qilingan bo'lsa @SQLRestriction uni "yo'q" qiladi
    private static FieldEntity fieldOf(AnalysisJob job) {
        try {
            FieldEntity f = job.getField();
            return f == null || f.getId() == null ? null : (f.getName() != null ? f : null);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultWithoutImages(AnalyzeResponse r) {
        Map<String, Object> m = JsonMapper.shared().convertValue(r, Map.class);
        m.remove("map_png_base64");                  // rasmlar — analysis_images da
        m.remove("chart_png_base64");
        if (m.get("debug") instanceof Map<?, ?> d) ((Map<String, Object>) d).remove("ndvi_map_png_base64");
        return m;
    }

    // varchar(500); xato matnida boshqaruv belgilari bo'lishi mumkin (NUL → PostgreSQL xatosi)
    private static String truncate(String s) {
        if (s == null) return null;
        String c = s.replaceAll("\\p{Cntrl}", " ");
        return c.length() <= 500 ? c : c.substring(0, 500);
    }
}
