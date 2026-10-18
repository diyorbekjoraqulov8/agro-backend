package uz.app.agro.problem;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.advisor.AdvisorProperties;
import uz.app.agro.audit.AuditEvent;
import uz.app.agro.audit.AuditService;
import uz.app.agro.bot.BotRunner;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.problem.dto.ProblemItem;
import uz.app.agro.problem.dto.ProblemSummary;
import uz.app.agro.problem.dto.ProblemSummary.ServiceHealth;
import uz.app.agro.problem.dto.ProblemSummary.Status;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.security.AuthUser;
import uz.app.agro.security.CurrentActor;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin "Muammolar": fermer/xizmat xatolari (ProblemRecorder yozadi) va tashqi xizmatlar holati */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemAdminService {

    public enum State { OPEN, RESOLVED, ALL }

    private final ProblemRepository problems;
    private final FarmerRepository farmers;
    private final FieldRepository fields;
    private final SatelliteClient satellite;
    private final AdvisorProperties advisor;
    private final ObjectProvider<BotRunner> bot;          // telegram.enabled=false — bean yo'q
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    @PreAuthorize(Permissions.CAN_READ_PROBLEMS)
    public PageResponse<ProblemItem> list(State state, ProblemSource source, ProblemEntity.Level level, Long farmerId,
                                          Pageable pageable) {
        Specification<ProblemEntity> spec = (root, q, cb) -> {
            var ps = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (state == State.OPEN) ps.add(cb.isNull(root.get("resolvedAt")));
            if (state == State.RESOLVED) ps.add(cb.isNotNull(root.get("resolvedAt")));
            if (source != null) ps.add(cb.equal(root.get("source"), source));
            if (level != null) ps.add(cb.equal(root.get("level"), level));
            if (farmerId != null) ps.add(cb.equal(root.get("farmerId"), farmerId));
            return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<ProblemEntity> page = problems.findAll(spec, pageable);
        // Ism va dala nomlari — sahifa uchun ikki so'rov (N+1 emas)
        Map<Long, FarmerEntity> fs = byId(farmers.findAllById(ids(page, ProblemEntity::getFarmerId)), FarmerEntity::getId);
        Map<Long, FieldEntity> fl = byId(fields.findAllById(ids(page, ProblemEntity::getFieldId)), FieldEntity::getId);
        return PageResponse.of(page.map(p -> toItem(p, fs.get(p.getFarmerId()), fl.get(p.getFieldId()), false)));
    }

    @PreAuthorize(Permissions.CAN_READ_PROBLEMS)
    public ProblemItem get(Long id) {
        ProblemEntity p = require(id);
        return toItem(p, p.getFarmerId() == null ? null : farmers.findById(p.getFarmerId()).orElse(null),
                p.getFieldId() == null ? null : fields.findById(p.getFieldId()).orElse(null), true);
    }

    /** Tashqi xizmatga HTTP (Python /health, 3 s) — DB tranzaksiyasi ochiq turmasin */
    @PreAuthorize(Permissions.CAN_READ_PROBLEMS)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ProblemSummary summary() {
        var sources = problems.summary(LocalDateTime.now().minusHours(24)).stream()
                .map(c -> new ProblemSummary.SourceCount(c.getSource(), c.getOpen(), c.getRecent(), c.getLastSeen()))
                .toList();
        return new ProblemSummary(services(), sources);
    }

    /** Menyu nishoni: ochiq xatolar (24 soatda takrorlangan) — yengil, tashqi xizmatlarni tekshirmaydi */
    @PreAuthorize(Permissions.CAN_READ_PROBLEMS)
    public long recentErrors() {
        return problems.countRecentErrors(LocalDateTime.now().minusHours(24));
    }

    @PreAuthorize(Permissions.CAN_MANAGE_PROBLEMS)
    @Transactional
    public ProblemItem resolve(Long id) {
        ProblemEntity p = require(id);
        if (p.getResolvedAt() == null) {
            p.setResolvedAt(LocalDateTime.now());
            p.setResolvedByEmail(actorEmail());
            auditService.event(AuditEvent.PROBLEM_RESOLVED).target("PROBLEM", id).detail("source", p.getSource()).record();
        }
        return toItem(p, null, null, false);
    }

    /** Manbaning barcha ochiq muammolari (masalan satellite qayta ishga tushirilgandan keyin) */
    @PreAuthorize(Permissions.CAN_MANAGE_PROBLEMS)
    @Transactional
    public int resolveAll(ProblemSource source) {
        int n = problems.resolveAll(source, LocalDateTime.now(), actorEmail());
        if (n > 0) auditService.event(AuditEvent.PROBLEM_RESOLVED).target("PROBLEM_SOURCE", null)
                .detail("source", source).detail("count", n).record();
        return n;
    }

    // ── Xizmatlar holati ───────────────────────────────────────────────────

    private List<ServiceHealth> services() {
        List<ServiceHealth> list = new ArrayList<>();
        list.add(new ServiceHealth("Ma'lumotlar bazasi", Status.UP, "PostgreSQL"));
        list.add(satellite.healthy()
                ? new ServiceHealth("Sun'iy yo'ldosh tahlili (Python)", Status.UP, "/health javob berdi")
                : new ServiceHealth("Sun'iy yo'ldosh tahlili (Python)", Status.DOWN, "Javob bermayapti — tahlil va monitoring kutib turibdi"));
        BotRunner runner = bot.getIfAvailable();
        String pollError = runner == null ? null : runner.recentPollError().orElse(null);
        list.add(runner == null ? new ServiceHealth("Telegram bot", Status.OFF, "Polling o'chiq (TELEGRAM_ENABLED / TELEGRAM_POLLING)")
                : !runner.isRunning() ? new ServiceHealth("Telegram bot", Status.DOWN, "Polling ishlamayapti")
                : pollError != null ? new ServiceHealth("Telegram bot", Status.WARN, pollError)
                : new ServiceHealth("Telegram bot", Status.UP, "Xabarlarni qabul qilyapti"));
        list.add(weather());
        list.add(claude());
        list.add(queue("Tahlil navbati",
                "SELECT count(*) FROM analysis_jobs WHERE status = 'PENDING' AND created_date < now() - interval '30 minutes'",
                "30 daqiqadan ko'p kutayotgan tahlil"));
        list.add(queue("Bildirishnomalar navbati",
                "SELECT count(*) FROM notifications WHERE status = 'PENDING' AND created_date < now() - interval '15 minutes'",
                "15 daqiqadan ko'p yuborilmagan xabar"));
        return list;
    }

    private ServiceHealth weather() {
        Timestamp last = jdbc.queryForObject("SELECT max(fetched_at) FROM weather_forecasts", Timestamp.class);
        if (last == null) return new ServiceHealth("Ob-havo (Open-Meteo)", Status.OFF, "Hali prognoz olinmagan");
        long hours = Duration.between(last.toLocalDateTime(), LocalDateTime.now()).toHours();
        return hours <= 6 ? new ServiceHealth("Ob-havo (Open-Meteo)", Status.UP, "Oxirgi prognoz " + hours + " soat oldin")
                : new ServiceHealth("Ob-havo (Open-Meteo)", Status.WARN, "Oxirgi prognoz " + hours + " soat oldin — yangilanmayapti");
    }

    private ServiceHealth claude() {
        if (!advisor.configured()) return new ServiceHealth("AI maslahat (Claude)", Status.OFF, "ANTHROPIC_API_KEY yo'q");
        Long failed = jdbc.queryForObject(
                "SELECT count(*) FROM advisor_requests WHERE status = 'FAILED' AND created_date > now() - interval '24 hours'", Long.class);
        return failed != null && failed > 0
                ? new ServiceHealth("AI maslahat (Claude)", Status.WARN, failed + " ta so'rov 24 soatda javobsiz qoldi")
                : new ServiceHealth("AI maslahat (Claude)", Status.UP, "Kalit bor, 24 soatda xato yo'q");
    }

    private ServiceHealth queue(String name, String sql, String what) {
        Long stuck = jdbc.queryForObject(sql, Long.class);
        return stuck != null && stuck > 0 ? new ServiceHealth(name, Status.WARN, stuck + " ta " + what)
                : new ServiceHealth(name, Status.UP, "Tiqilib qolgan yo'q");
    }

    // ── Yordamchilar ───────────────────────────────────────────────────────

    private ProblemEntity require(Long id) {
        return problems.findById(id).orElseThrow(() -> new NotFoundException("Muammo", id));
    }

    private static String actorEmail() {
        return CurrentActor.get().map(AuthUser::email).orElse(null);
    }

    private static ProblemItem toItem(ProblemEntity p, FarmerEntity farmer, FieldEntity field, boolean withDetail) {
        String name = farmer == null ? null : farmer.getFullName() != null ? farmer.getFullName()
                : farmer.getTelegramUsername() != null ? "@" + farmer.getTelegramUsername() : null;
        return new ProblemItem(p.getId(), p.getSource(), p.getLevel(), p.getFarmerId(), name, p.getFieldId(),
                field == null ? null : field.getName(), p.getRefType(), p.getRefId(), p.getMessage(),
                withDetail ? p.getDetail() : null, p.getOccurrences(), p.getFirstSeen(), p.getLastSeen(),
                p.getResolvedAt(), p.getResolvedByEmail());
    }

    private static Set<Long> ids(Page<ProblemEntity> page, Function<ProblemEntity, Long> f) {
        return page.stream().map(f).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
