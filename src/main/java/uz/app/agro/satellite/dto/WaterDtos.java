package uz.app.agro.satellite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Python app/water.py va /ndvi/latest shartnomasi (snake_case) */
public final class WaterDtos {

    private WaterDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BalanceRequest(double lat, double lon, LocalDate lastIrrigatedOn, double kc, double tawMm, double rawMm) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BalanceResponse(LocalDate localDate, int utcOffsetSeconds, String timezone,
                                  double depletionMm, double depletionPct, double rawMm, double tawMm,
                                  boolean due, Integer daysUntil, LocalDate irrigateOn,
                                  // ⚠️ SnakeCaseStrategy raqamdan oldin "_" qo'ymaydi: rainNext3dMm → "rain_next3d_mm"
                                  @JsonProperty("rain_next_3d_mm") double rainNext3dMm,
                                  double etcTodayMm, List<Day> days) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Day(LocalDate date, double et0Mm, double rainMm, double depletionMm, boolean forecast) {}

    public record SoilRequest(double lat, double lon) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SoilResponse(String texture, double sandPct, double clayPct) {}

    public record NdviRequest(Map<String, Object> geometry) {}

    /** C3 /monitor: since — oxirgi ko'rilgan surat (null — birinchi tekshiruv); drop — piksel "pasaydi" chegarasi (ΔNDVI) */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MonitorRequest(Map<String, Object> geometry, LocalDate since, double drop, double mapMinPct) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonitorResponse(boolean newScene, LocalDate sceneDate, LocalDate refDate, Double ndvi, Double ndviRef,
                                  Double declinePct, String declineSide, Double improvePct, String mapPngBase64,
                                  LocalDate latestSeen) {}

    /** R4 /field/snapshot: oxirgi ochiq surat — dala xaritasi (mutlaq NDVI) va eng sust joy */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapshotResponse(LocalDate sceneDate, double ndvi, double weakPct, Double weakLat, Double weakLon,
                                   String mapPngBase64) {}

    /** R2 /boundary/detect: fermer dala ichiga bosgan nuqta */
    public record BoundaryRequest(double lat, double lon) {}

    /** coordinates — [[lon, lat], ...] yopiq halqa; confidence HIGH | LOW (chetga tekkan, juda kichik) */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoundaryResponse(List<List<Double>> coordinates, double areaHa, LocalDate sceneDate, String confidence,
                                   boolean touchesEdge) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NdviResponse(double ndvi, LocalDate date, int scenes) {}
}
