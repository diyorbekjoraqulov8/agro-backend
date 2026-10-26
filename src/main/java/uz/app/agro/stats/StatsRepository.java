package uz.app.agro.stats;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uz.app.agro.farmer.FarmerEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Faqat o'qish, native agregatlar. @SQLRestriction native'ga qo'llanmaydi — deleted qo'lda
interface StatsRepository extends Repository<FarmerEntity, Long> {

    interface SourceRow {
        String getSource();
        long getFarmers();
        long getWithField();
        long getAnalyzed();
        long getLeads();
    }

    interface CountRow {
        String getKey();
        long getCount();
    }

    interface Totals {
        long getFarmers();
        long getFields();
        BigDecimal getTotalAreaHa();
        long getLeads();
    }

    // Voronka: kelgan → dala chizgan → tahlil olgan → lead. Qaysi reklama kanali pulga arziydi
    @Query(value = """
            SELECT COALESCE(f.source, '(direct)') AS source,
                   count(*) AS farmers,
                   count(*) FILTER (WHERE EXISTS (
                       SELECT 1 FROM fields fl WHERE fl.farmer_id = f.id AND fl.deleted = FALSE)) AS withField,
                   count(*) FILTER (WHERE EXISTS (
                       SELECT 1 FROM analysis_jobs j JOIN fields fl ON fl.id = j.field_id
                        WHERE fl.farmer_id = f.id AND j.status = 'DONE')) AS analyzed,
                   count(*) FILTER (WHERE f.lead_status IN ('REQUESTED', 'CONTACTED', 'PILOT')) AS leads
              FROM farmers f
             WHERE f.deleted = FALSE AND f.lab = FALSE AND f.created_date >= :from AND f.created_date < :to
             GROUP BY 1
             ORDER BY farmers DESC
            """, nativeQuery = true)
    List<SourceRow> bySource(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT (SELECT count(*) FROM farmers WHERE deleted = FALSE AND lab = FALSE) AS farmers,
                   (SELECT count(*) FROM fields  WHERE deleted = FALSE AND farmer_id NOT IN (SELECT id FROM farmers WHERE lab)) AS fields,
                   (SELECT COALESCE(sum(area_ha), 0) FROM fields WHERE deleted = FALSE
                       AND farmer_id NOT IN (SELECT id FROM farmers WHERE lab)) AS totalAreaHa,
                   (SELECT count(*) FROM farmers WHERE deleted = FALSE
                       AND lead_status IN ('REQUESTED', 'CONTACTED', 'PILOT')) AS leads
            """, nativeQuery = true)
    Totals totals();

    @Query(value = """
            SELECT j.status AS key, count(*) AS count FROM analysis_jobs j
              JOIN fields fl ON fl.id = j.field_id JOIN farmers f ON f.id = fl.farmer_id
             WHERE f.lab = FALSE GROUP BY j.status
            """, nativeQuery = true)
    List<CountRow> analysesByStatus();

    @Query(value = """
            SELECT lead_status AS key, count(*) AS count FROM farmers
             WHERE deleted = FALSE AND lab = FALSE GROUP BY lead_status
            """, nativeQuery = true)
    List<CountRow> farmersByLeadStatus();

    @Query(value = """
            SELECT COALESCE(country_code, '??') AS key, count(*) AS count FROM farmers
             WHERE deleted = FALSE AND lab = FALSE GROUP BY 1 ORDER BY count DESC LIMIT 20
            """, nativeQuery = true)
    List<CountRow> farmersByCountry();
}
