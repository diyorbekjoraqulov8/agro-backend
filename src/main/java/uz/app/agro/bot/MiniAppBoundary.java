package uz.app.agro.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.app.agro.satellite.SatelliteClient;
import uz.app.agro.satellite.SatelliteRejectedException;
import uz.app.agro.satellite.SatelliteUnavailableException;
import uz.app.agro.satellite.dto.WaterDtos.BoundaryResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * R2 "🪄 bosib belgilash": Mini App → shu yer → Python /boundary/detect. Python og'ir (Sentinel-2 o'qish) — cheklovlar:
 * fermer boshiga soatiga LIMIT ta, bir vaqtda ko'pi bilan PARALLEL ta (qolgani darhol BUSY, navbat yo'q — fermer kutib
 * qolmasin, qo'lda chizadi). Bitta instans uchun xotirada (prod — bitta backend).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
class MiniAppBoundary {

    static final int LIMIT = 20;
    static final Duration WINDOW = Duration.ofHours(1);
    private static final int PARALLEL = 3;

    private final SatelliteClient satellite;
    private final Map<Long, Deque<Instant>> recent = new ConcurrentHashMap<>();
    private final Semaphore slots = new Semaphore(PARALLEL);
    private final java.util.Set<Long> inFlight = ConcurrentHashMap.newKeySet();   // fermer boshiga bir vaqtda bitta

    /** outcome: OK | RATE_LIMIT | BUSY | UNAVAILABLE | Python rad etish kodi (NO_CLEAR_SCENE, POINT_NOT_CLEAR, ...) */
    record Result(String outcome, BoundaryResponse boundary) {}

    Result detect(long tg, double lat, double lon) {
        if (!inFlight.add(tg)) return new Result("BUSY", null);
        try {
            return detectOnce(tg, lat, lon);
        } finally {
            inFlight.remove(tg);
        }
    }

    private Result detectOnce(long tg, double lat, double lon) {
        if (!allow(tg)) return new Result("RATE_LIMIT", null);
        if (!slots.tryAcquire()) return new Result("BUSY", null);
        try {
            BoundaryResponse r = satellite.detectBoundary(lat, lon);
            log.info("mini app 🪄: tg {} → {} ga, {}", tg, r.areaHa(), r.confidence());
            return new Result("OK", r);
        } catch (SatelliteRejectedException e) {
            return new Result(e.getCode(), null);
        } catch (SatelliteUnavailableException e) {
            log.warn("mini app 🪄: satellite: {}", e.getMessage());
            // Python 503 — og'ir so'rovlar navbati to'la (xizmat tirik): "band, birozdan keyin"
            return new Result(Integer.valueOf(503).equals(e.status()) ? "BUSY" : "UNAVAILABLE", null);
        } finally {
            slots.release();
        }
    }

    private boolean allow(long tg) {
        Instant now = Instant.now();
        // Eski oynalar tozalanadi — xarita cheksiz o'smasin
        if (recent.size() > 10_000) recent.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Instant last = e.getValue().peekLast();
                return last == null || last.isBefore(now.minus(WINDOW));
            }
        });
        Deque<Instant> q = recent.computeIfAbsent(tg, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) q.pollFirst();
            if (q.size() >= LIMIT) return false;
            q.addLast(now);
            return true;
        }
    }
}
