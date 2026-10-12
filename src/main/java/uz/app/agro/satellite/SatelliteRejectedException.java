package uz.app.agro.satellite;

import lombok.Getter;

import java.util.Map;

// 422 (poligon yaroqsiz) yoki 404 (surat yo'q) — qayta urinish FOYDASIZ, fermerga tushunarli xabar
@Getter
public class SatelliteRejectedException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> params;

    public SatelliteRejectedException(int status, String code, Map<String, Object> params) {
        super("satellite rad etdi: " + status + " " + code);
        this.status = status;
        this.code = code;
        this.params = params == null ? Map.of() : params;
    }
}
