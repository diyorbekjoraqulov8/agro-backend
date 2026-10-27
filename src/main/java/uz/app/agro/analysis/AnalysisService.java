package uz.app.agro.analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.analysis.dto.AnalysisListItem;
import uz.app.agro.analysis.dto.AnalysisResponse;
import uz.app.agro.analysis.dto.RunAnalysisRequest;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.BadRequestException;
import uz.app.agro.common.exception.ConflictException;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.rbac.Permissions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    // Tahlil vaqti oynaga proporsional (Python har sahnani o'qiydi) — chegarasiz oyna worker'ni band qiladi
    static final int MAX_WINDOW_DAYS = 400;
    private static final List<AnalysisJob.Status> ACTIVE =
            List.of(AnalysisJob.Status.PENDING, AnalysisJob.Status.RUNNING);

    private final AnalysisJobRepository jobRepository;
    private final AnalysisImageRepository imageRepository;
    private final AnalysisImages images;
    private final FieldRepository fieldRepository;
    private final AnalysisMapper analysisMapper;
    private final AuditService auditService;

    /** Global tahlil oynasi: oxirgi 12 oy. Mavsumni Python NDVI'dan topadi (kalendar jadvali yo'q). */
    public static Window defaultWindow(LocalDate today) {
        return new Window(today.minusDays(365), today);
    }

    public record Window(LocalDate start, LocalDate end) {}

    @PreAuthorize(Permissions.CAN_READ_ANALYSIS)
    public PageResponse<AnalysisListItem> search(Long fieldId, AnalysisJob.Status status,
                                                 AnalysisJob.Kind kind, Pageable pageable) {
        return PageResponse.of(jobRepository
                .findAll(AnalysisSpecs.filter(fieldId, status, kind), pageable)
                .map(analysisMapper::toListItem));
    }

    @PreAuthorize(Permissions.CAN_READ_ANALYSIS)
    public AnalysisResponse get(Long id) {
        return analysisMapper.toResponse(find(id));
    }

    /** Rasm faqat DONE tahlilda bor; yo'q bo'lsa — 404 (tahlil yo'q yoki hali tugamagan). */
    @PreAuthorize(Permissions.CAN_READ_ANALYSIS)
    public byte[] image(Long id, boolean map) {
        AnalysisImage img = imageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tahlil rasmi", id));
        return map ? images.map(img) : images.chart(img);
    }

    /** L1b debug: mavsum cho'qqisi NDVI xaritasi. Eski tahlil yoki saqlanmagan bo'lsa — 404 */
    @PreAuthorize(Permissions.CAN_READ_ANALYSIS)
    public byte[] ndviImage(Long id) {
        AnalysisImage img = imageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tahlil rasmi", id));
        return images.ndvi(img);
    }

    /** Xodim tahlilni qo'lda ishga tushiradi. STAFF — fermerning bepul kvotasiga kirmaydi. */
    @PreAuthorize(Permissions.CAN_RUN_ANALYSIS)
    @Transactional
    public AnalysisResponse run(Long fieldId, RunAnalysisRequest req) {
        FieldEntity field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new NotFoundException("Dala", fieldId));
        Window w = window(req);
        // Tez javob uchun; haqiqiy kafolat — V11 dagi qisman unique index (parallel so'rovlar)
        if (jobRepository.existsByFieldIdAndStatusIn(fieldId, ACTIVE)) {
            throw new ConflictException("Bu dalada tahlil allaqachon navbatda yoki bajarilmoqda");
        }
        AnalysisJob job = new AnalysisJob();
        job.setField(field);
        job.setKind(AnalysisJob.Kind.STAFF);
        job.setCrop(field.getCrop());
        job.setSeasonStart(w.start());
        job.setSeasonEnd(w.end());
        jobRepository.save(job);

        auditService.event(AuditEvent.ANALYSIS_REQUESTED)
                .target("ANALYSIS", job.getId())
                .detail("fieldId", fieldId)
                .detail("kind", job.getKind().name())
                .record();
        return analysisMapper.toResponse(job);
    }

    /** FAILED → PENDING. Urinishlar sanog'i nollanadi — xodim muammoni (masalan kalitni) tuzatgan. */
    @PreAuthorize(Permissions.CAN_RUN_ANALYSIS)
    @Transactional
    public AnalysisResponse retry(Long id) {
        AnalysisJob job = find(id);
        if (job.getStatus() != AnalysisJob.Status.FAILED) {
            throw new ConflictException("Faqat FAILED holatidagi tahlilni qayta ishga tushirish mumkin");
        }
        String oldError = job.getError();
        job.setStatus(AnalysisJob.Status.PENDING);
        job.setAttempts(0);
        job.setError(null);
        job.setNextTryAt(LocalDateTime.now());
        job.setStartedAt(null);
        job.setFinishedAt(null);

        auditService.event(AuditEvent.ANALYSIS_RETRIED)
                .target("ANALYSIS", id)
                .detail("previousError", oldError)
                .record();
        return analysisMapper.toResponse(job);
    }

    private Window window(RunAnalysisRequest req) {
        LocalDate today = LocalDate.now();
        if (req == null || (req.seasonStart() == null && req.seasonEnd() == null)) {
            return defaultWindow(today);
        }
        if (req.seasonStart() == null || req.seasonEnd() == null) {
            throw new BadRequestException("seasonStart va seasonEnd ikkalasi birga beriladi yoki ikkalasi ham bo'sh");
        }
        if (!req.seasonStart().isBefore(req.seasonEnd())) {
            throw new BadRequestException("seasonStart seasonEnd dan oldin bo'lishi kerak");
        }
        if (req.seasonEnd().isAfter(today)) {
            throw new BadRequestException("seasonEnd kelajakda bo'lishi mumkin emas");
        }
        if (ChronoUnit.DAYS.between(req.seasonStart(), req.seasonEnd()) > MAX_WINDOW_DAYS) {
            throw new BadRequestException("Oyna ko'pi bilan " + MAX_WINDOW_DAYS + " kun");
        }
        if (req.seasonStart().isBefore(LocalDate.of(2017, 1, 1))) {
            // Sentinel-2 C1 2017 dan beri bir xil qayta ishlangan (qo'llanma S2 qarori)
            throw new BadRequestException("seasonStart 2017-01-01 dan oldin bo'lmasin");
        }
        return new Window(req.seasonStart(), req.seasonEnd());
    }

    private AnalysisJob find(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new NotFoundException("Tahlil", id));
    }
}
