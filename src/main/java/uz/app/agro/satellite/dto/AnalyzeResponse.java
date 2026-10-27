package uz.app.agro.satellite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Python yangi maydon qo'shsa Java yiqilmasin — ignoreUnknown
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzeResponse(
        long fieldId,
        double areaHa,
        String countryCode,
        double centroidLat,
        double centroidLon,
        int scenesUsed,
        int scenesRejectedCloud,
        Zones zones,
        double peakNdvi,
        LocalDate peakDate,
        Double prevYearPeakNdvi,           // o'tgan yil ma'lumoti bo'lmasa null
        Period growingSeason,              // ekin topilmasa null
        List<Period> stressPeriods,
        Water water,                       // mavsum topilmasa null
        List<Point> timeseries,
        List<Recommendation> recommendations,

        // Jackson base64 satrni byte[] ga o'zi ochadi
        @JsonProperty("map_png_base64") byte[] mapPng,
        @JsonProperty("chart_png_base64") byte[] chartPng,
        Debug debug                        // L1b; eski satellite-service bo'lsa null
) {

    /** Tahlil nimaga asoslangani — admin aniqlikni tekshiradi. ndviMapPng — fayl servisiga, qolgani result jsonb'ga */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Debug(List<Scene> scenes, double searchCloudLt, double minClearPct, double goodRel, double weakRel,
                        double peakNdviMedian, int pixels, Double weakLat, Double weakLon,
                        @JsonProperty("ndvi_map_png_base64") byte[] ndviMapPng) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Scene(LocalDate date, Double cloudPct, double clearPct, boolean used) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Zones(double goodPct, double midPct, double weakPct, String weakSide) {}

    public record Period(LocalDate from, LocalDate to) {}

    public record Point(LocalDate date, double ndvi, double ndmi) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Water(double et0SumMm, double etcSumMm, double effectiveRainMm,
                        int irrigationNeedM3Ha, int irrigationNeedLowM3Ha, int irrigationNeedHighM3Ha) {}

    public record Recommendation(String code, Map<String, Object> params) {}
}
