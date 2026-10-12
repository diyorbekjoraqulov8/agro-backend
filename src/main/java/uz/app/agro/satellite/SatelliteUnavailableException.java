package uz.app.agro.satellite;

// Tarmoq, 5xx, timeout — qayta urinish mumkin (worker attempts++, next_try_at)
public class SatelliteUnavailableException extends RuntimeException {

    public SatelliteUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
