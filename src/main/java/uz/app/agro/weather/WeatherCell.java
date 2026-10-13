package uz.app.agro.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prognoz katagi: koordinata 0.1° ga yaxlitlanadi (~10 km). Bir qishloqdagi dalalar — bitta katak, bitta so'rov.
 * Shamol/harorat uchun 10 km aniqlik yetarli (modellar to'ri ham 2–11 km).
 */
public record WeatherCell(String key, BigDecimal lat, BigDecimal lon) {

    public static WeatherCell of(BigDecimal lat, BigDecimal lon) {
        BigDecimal la = lat.setScale(1, RoundingMode.HALF_UP), lo = lon.setScale(1, RoundingMode.HALF_UP);
        return new WeatherCell(la.toPlainString() + ":" + lo.toPlainString(), la, lo);
    }
}
