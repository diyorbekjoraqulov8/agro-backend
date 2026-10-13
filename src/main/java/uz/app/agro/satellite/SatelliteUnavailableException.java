package uz.app.agro.satellite;

// Tarmoq, 5xx, timeout — qayta urinish mumkin (worker attempts++, next_try_at)
public class SatelliteUnavailableException extends RuntimeException {

    /** HTTP holati; null — ulanib bo'lmadi / timeout */
    private final Integer status;

    public SatelliteUnavailableException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public SatelliteUnavailableException(String message, Throwable cause, Integer status) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Xizmatning o'zi ishlamayapti (ulanish, 502/503/504) — keyingi so'rovlar ham bo'lmaydi.
     * 500 — aniq bir so'rov (masalan "zaharli" dala) xatosi: qolganlarini to'xtatmaslik kerak.
     */
    public boolean serviceDown() {
        return status == null || status == 502 || status == 503 || status == 504;
    }
}
