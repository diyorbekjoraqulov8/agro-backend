package uz.app.agro.weather;

/** Prognoz xizmati javob bermadi — job keyingi soatda qayta uradi, bot eski keshni ko'rsatadi */
public class WeatherUnavailableException extends RuntimeException {
    public WeatherUnavailableException(String message) {
        super(message);
    }
}
