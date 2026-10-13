package uz.app.agro.weather;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecastEntity, String> {

    /** Upsert — job va bot (yoki ikki instans) bir katakni bir vaqtda yozsa PK xatosi bo'lmasin */
    @Modifying
    @Query(value = """
            INSERT INTO weather_forecasts (cell_key, lat, lon, timezone, utc_offset_seconds, days, fetched_at)
            VALUES (:key, :lat, :lon, :tz, :offset, CAST(:days AS jsonb), now())
            ON CONFLICT (cell_key) DO UPDATE SET timezone = EXCLUDED.timezone, utc_offset_seconds = EXCLUDED.utc_offset_seconds,
                   days = EXCLUDED.days, fetched_at = EXCLUDED.fetched_at
            """, nativeQuery = true)
    void upsert(@Param("key") String key, @Param("lat") BigDecimal lat, @Param("lon") BigDecimal lon, @Param("tz") String tz,
                @Param("offset") Integer offset, @Param("days") String daysJson);

    interface FieldPoint {
        Long getFieldId();
        String getFieldName();
        Long getFarmerId();
        BigDecimal getLat();
        BigDecimal getLon();
    }

    /** Ogohlantirish oluvchilar: o'chirilmagan dala (markazi bor) va botni bloklamagan fermer — bitta so'rov */
    @Query(value = """
            SELECT f.id AS fieldId, f.name AS fieldName, f.farmer_id AS farmerId, f.centroid_lat AS lat, f.centroid_lon AS lon
              FROM fields f JOIN farmers fa ON fa.id = f.farmer_id
             WHERE f.deleted = FALSE AND fa.deleted = FALSE AND fa.blocked_bot = FALSE AND f.centroid_lat IS NOT NULL
             ORDER BY f.id
            """, nativeQuery = true)
    List<FieldPoint> fieldPoints();

    /** Bot "🌦 Ob-havo": shu fermerning dalalari */
    @Query(value = """
            SELECT f.id AS fieldId, f.name AS fieldName, f.farmer_id AS farmerId, f.centroid_lat AS lat, f.centroid_lon AS lon
              FROM fields f JOIN farmers fa ON fa.id = f.farmer_id
             WHERE f.deleted = FALSE AND fa.deleted = FALSE AND fa.telegram_user_id = :tg AND f.centroid_lat IS NOT NULL
             ORDER BY f.id DESC
            """, nativeQuery = true)
    List<FieldPoint> fieldPointsOf(@Param("tg") long telegramUserId);
}
