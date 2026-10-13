package uz.app.agro.satellite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.app.agro.satellite.dto.AnalyzeRequest;
import uz.app.agro.satellite.dto.AnalyzeResponse;
import uz.app.agro.satellite.dto.PdfRequest;
import uz.app.agro.satellite.dto.WaterDtos;

import java.net.http.HttpClient;
import java.util.Map;

// Tahlil 20-90 s: bu chaqiruv HECH QACHON tranzaksiya ichida bo'lmasin (Hikari pool tugaydi)
@Slf4j
@Component
public class SatelliteClient {

    private final RestClient rest;

    public SatelliteClient(SatelliteProperties props) {
        // HTTP_1_1 MAJBURIY: default HTTP/2 da JDK "Upgrade: h2c" + chunked tana yuboradi,
        // uvicorn h2c ni qo'llamaydi va tanani bo'sh ko'radi → jimgina 422
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build());
        factory.setReadTimeout(props.readTimeout());
        this.rest = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("X-Internal-Key", props.internalKey())
                .requestFactory(factory)
                .build();
    }

    public AnalyzeResponse analyze(AnalyzeRequest req) {
        try {
            return rest.post().uri("/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .onStatus(s -> s.value() == 422 || s.value() == 404, (rq, rs) -> {
                        throw rejected(rs.getStatusCode(), rs.getBody().readAllBytes());
                    })
                    .onStatus(s -> s.value() == 401, (rq, rs) -> {
                        // Kalit mos emas — konfiguratsiya xatosi, qayta urinish yordam bermaydi
                        log.error("satellite 401: SATELLITE_INTERNAL_KEY Python'dagi INTERNAL_KEY bilan mos emas");
                        throw new SatelliteUnavailableException("satellite 401 (kalit)", null);
                    })
                    .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                        throw new SatelliteUnavailableException("satellite " + rs.getStatusCode().value(), null);
                    })
                    .body(AnalyzeResponse.class);
        } catch (ResourceAccessException e) {          // ulanib bo'lmadi, timeout
            throw new SatelliteUnavailableException("satellite ulanish: " + e.getMessage(), e);
        } catch (SatelliteRejectedException | SatelliteUnavailableException e) {
            throw e;
        } catch (RestClientException e) {              // javobni o'qib bo'lmadi (shartnoma buzilgan)
            throw new SatelliteUnavailableException("satellite javobi: " + e.getMessage(), e);
        }
    }

    /** Sug'orish balansi (Open-Meteo, ~1 s) */
    public WaterDtos.BalanceResponse waterBalance(WaterDtos.BalanceRequest req) {
        return post("/water/balance", req, WaterDtos.BalanceResponse.class);
    }

    /** SoilGrids tuproq turi (~1,5 s). 404 NO_SOIL_DATA — suv/shahar */
    public WaterDtos.SoilResponse soil(double lat, double lon) {
        return post("/soil", new WaterDtos.SoilRequest(lat, lon), WaterDtos.SoilResponse.class);
    }

    /** So'nggi 30 kundagi NDVI (Kc uchun, ~10 s). 404 NO_SCENES — bulutli */
    public WaterDtos.NdviResponse latestNdvi(java.util.Map<String, Object> geometry) {
        return post("/ndvi/latest", new WaterDtos.NdviRequest(geometry), WaterDtos.NdviResponse.class);
    }

    /** C3: yangi surat bo'lsa — oldingisi bilan solishtirish (yangi yo'q — ~2 s, bor — ~10 s) */
    public WaterDtos.MonitorResponse monitor(WaterDtos.MonitorRequest req) {
        return post("/monitor", req, WaterDtos.MonitorResponse.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return rest.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(s -> s.value() == 422 || s.value() == 404, (rq, rs) -> {
                        throw rejected(rs.getStatusCode(), rs.getBody().readAllBytes());
                    })
                    .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                        throw new SatelliteUnavailableException("satellite " + path + " " + rs.getStatusCode().value(), null,
                                rs.getStatusCode().value());
                    })
                    .body(type);
        } catch (ResourceAccessException e) {
            throw new SatelliteUnavailableException("satellite ulanish: " + e.getMessage(), e);
        } catch (SatelliteRejectedException | SatelliteUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            throw new SatelliteUnavailableException("satellite javobi " + path + ": " + e.getMessage(), e);
        }
    }

    /** Hisobot PDF'i (Python matplotlib, ~0,3 s). Xato — SatelliteUnavailableException */
    public byte[] reportPdf(PdfRequest req) {
        try {
            return rest.post().uri("/report/pdf")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (rq, rs) -> {
                        throw new SatelliteUnavailableException("satellite pdf " + rs.getStatusCode().value(), null);
                    })
                    .body(byte[].class);
        } catch (ResourceAccessException e) {
            throw new SatelliteUnavailableException("satellite ulanish: " + e.getMessage(), e);
        }
    }

    // {"code": "...", "params": {...}} — Python'dagi Rejected handler
    @SuppressWarnings("unchecked")
    private SatelliteRejectedException rejected(HttpStatusCode status, byte[] body) {
        String code = "UNKNOWN";
        Map<String, Object> params = Map.of();
        try {
            Map<String, Object> m = tools.jackson.databind.json.JsonMapper.shared().readValue(body, Map.class);
            if (m.get("code") instanceof String c) code = c;
            if (m.get("params") instanceof Map<?, ?> p) params = (Map<String, Object>) p;
        } catch (RuntimeException e) {
            log.warn("satellite {} javobi o'qilmadi", status.value());
        }
        return new SatelliteRejectedException(status.value(), code, params);
    }
}
