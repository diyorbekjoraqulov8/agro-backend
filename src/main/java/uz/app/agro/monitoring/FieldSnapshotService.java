package uz.app.agro.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import uz.app.agro.field.FieldEntity;
import uz.app.agro.field.FieldRepository;
import uz.app.agro.file.FileRegistry;
import uz.app.agro.problem.ProblemRecorder;
import uz.app.agro.problem.ProblemSource;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.satellite.SatelliteRejectedException;
import uz.app.agro.satellite.SatelliteUnavailableException;
import uz.app.agro.satellite.dto.WaterDtos.SnapshotResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * R4 "📸 Bugungi rasm" (bot chaqiradi, @PreAuthorize YO'Q, egalik — telegramUserId). Kesh: oxirgi rasm monitoring
 * ko'rgan oxirgi suratdan eski bo'lmasa va 24 soatdan yangi bo'lsa — Python chaqirilmaydi.
 * Python (tarmoq, ~10 s) tranzaksiyadan TASHQARIDA; DB qismlari — qisqa TransactionTemplate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldSnapshotService {

    public enum Status { OK, NONE, FAILED, NOT_FOUND }

    public record Snapshot(long id, String fieldName, LocalDate sceneDate, double ndvi, double weakPct,
                           Double weakLat, Double weakLon, String fileHashId, String telegramFileId) {}

    public record Result(Status status, Snapshot snapshot) {}

    private final FieldRepository fields;
    private final FieldMonitoringRepository monitoring;
    private final FieldSnapshotRepository snapshots;
    private final SatelliteClient satellite;
    private final FileRegistry files;
    private final TransactionTemplate tx;
    private final ProblemRecorder problems;

    /** Surat yo'q (bulut) yoki Python xatosi — shu vaqtgacha qayta so'ralmaydi (har bosishda Python ishlamasin) */
    private static final java.time.Duration NEGATIVE_TTL = java.time.Duration.ofHours(2);
    private final Map<Long, java.time.Instant> negative = new java.util.concurrent.ConcurrentHashMap<>();

    private record Target(String name, Map<String, Object> geometry, LocalDate knownScene, FieldSnapshotEntity cached) {}

    public Result get(long telegramUserId, long fieldId) {
        Target t = tx.execute(s -> fields.findById(fieldId)
                .filter(f -> f.getFarmer().getTelegramUserId() == telegramUserId)
                .map(f -> new Target(f.getName(), new HashMap<>(f.getBoundaryGeojson()),
                        monitoring.findById(fieldId).map(FieldMonitoringEntity::getLastSceneDate).orElse(null),
                        snapshots.findFirstByFieldIdOrderByCreatedDateDesc(fieldId).orElse(null)))
                .orElse(null));
        if (t == null) return new Result(Status.NOT_FOUND, null);
        FieldSnapshotEntity c = t.cached();
        if (c != null && c.getCreatedDate().isAfter(LocalDateTime.now().minusHours(24))
                && (t.knownScene() == null || !c.getSceneDate().isBefore(t.knownScene()))) {
            return new Result(Status.OK, toSnapshot(c, t.name()));
        }
        java.time.Instant blockedUntil = negative.get(fieldId);
        if (blockedUntil != null && blockedUntil.isAfter(java.time.Instant.now())) {
            return c != null ? new Result(Status.OK, toSnapshot(c, t.name())) : new Result(Status.NONE, null);
        }
        SnapshotResponse r;
        try {
            r = satellite.snapshot(t.geometry());
        } catch (SatelliteRejectedException e) {
            // Surat yo'q (bulut) — eski rasm bo'lsa o'shani beramiz
            negative.put(fieldId, java.time.Instant.now().plus(NEGATIVE_TTL));
            return c != null ? new Result(Status.OK, toSnapshot(c, t.name())) : new Result(Status.NONE, null);
        } catch (SatelliteUnavailableException e) {
            log.warn("snapshot: dala {}: {}", fieldId, e.getMessage());
            problems.error(ProblemSource.MONITORING, "Bugungi rasm yasalmadi (Python): " + e.getMessage()).field(fieldId).record();
            return c != null ? new Result(Status.OK, toSnapshot(c, t.name())) : new Result(Status.FAILED, null);
        }
        negative.remove(fieldId);
        if (c != null && c.getSceneDate().equals(r.sceneDate())) {
            // Surat o'sha — yangi fayl/qator emas, keshni "yangiladik" deb belgilaymiz
            tx.executeWithoutResult(s -> snapshots.findById(c.getId()).ifPresent(x -> x.setCreatedDate(LocalDateTime.now())));
            return new Result(Status.OK, toSnapshot(c, t.name()));
        }
        String hashId = files.store(Base64.getDecoder().decode(r.mapPngBase64()),
                "snapshot-" + fieldId + "-" + r.sceneDate() + ".png", FileRegistry.Uploader.system()).hashId();
        FieldSnapshotEntity e = new FieldSnapshotEntity();
        e.setFieldId(fieldId);
        e.setSceneDate(r.sceneDate());
        e.setFileHashId(hashId);
        e.setNdvi(dec(r.ndvi(), 3));
        e.setWeakPct(dec(r.weakPct(), 1));
        e.setWeakLat(r.weakLat() == null ? null : dec(r.weakLat(), 6));
        e.setWeakLon(r.weakLon() == null ? null : dec(r.weakLon(), 6));
        e.setCreatedDate(LocalDateTime.now());
        tx.executeWithoutResult(s -> snapshots.save(e));
        return new Result(Status.OK, toSnapshot(e, t.name()));
    }

    /** Egalik (tugma bosilganda — "tayyorlanmoqda" deyishdan va limitdan oldin) */
    public boolean owns(long telegramUserId, long fieldId) {
        return Boolean.TRUE.equals(tx.execute(s -> fields.findById(fieldId)
                .map(f -> f.getFarmer().getTelegramUserId() == telegramUserId).orElse(false)));
    }

    /** "📍 Borib ko'rish" — oxirgi rasmdagi eng sust joy (egalik tekshiriladi) */
    public Optional<Snapshot> latest(long telegramUserId, long fieldId) {
        return tx.execute(s -> fields.findById(fieldId)
                .filter(f -> f.getFarmer().getTelegramUserId() == telegramUserId)
                .flatMap(f -> snapshots.findFirstByFieldIdOrderByCreatedDateDesc(fieldId).map(c -> toSnapshot(c, f.getName()))));
    }

    public void rememberTelegramId(long snapshotId, String telegramFileId) {
        tx.executeWithoutResult(s -> snapshots.rememberTelegramId(snapshotId, telegramFileId));
    }

    private static Snapshot toSnapshot(FieldSnapshotEntity e, String name) {
        return new Snapshot(e.getId(), name, e.getSceneDate(), e.getNdvi().doubleValue(), e.getWeakPct().doubleValue(),
                e.getWeakLat() == null ? null : e.getWeakLat().doubleValue(), e.getWeakLon() == null ? null : e.getWeakLon().doubleValue(),
                e.getFileHashId(), e.getTelegramFileId());
    }

    private static BigDecimal dec(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
