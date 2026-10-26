package uz.app.agro.notification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.app.agro.notification.dto.Audience;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Audience → fermerlar. SQL qismlari — faqat o'zimizning shablonlar; foydalanuvchi qiymatlari — FAQAT parametr
 * (injection yo'q). Bloklaganlar va o'chirilganlar hech qachon kirmaydi.
 */
@Component
@RequiredArgsConstructor
class AudienceQuery {

    private final EntityManager em;

    record Target(long farmerId, String language, String name) {}

    List<Target> targets(Audience a, Integer limit) {
        StringBuilder sql = new StringBuilder("SELECT f.id, f.language, coalesce(f.full_name, f.telegram_username, '#' || f.id) FROM farmers f WHERE ");
        Map<String, Object> params = where(a, sql);
        sql.append(" ORDER BY f.id");
        if (limit != null) sql.append(" LIMIT ").append(limit);
        Query q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<Target> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) out.add(new Target(((Number) r[0]).longValue(), (String) r[1], (String) r[2]));
        return out;
    }

    Map<String, Long> countByLanguage(Audience a) {
        StringBuilder sql = new StringBuilder("SELECT f.language, count(*) FROM farmers f WHERE ");
        Map<String, Object> params = where(a, sql);
        sql.append(" GROUP BY f.language");
        Query q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        Map<String, Long> out = new HashMap<>();
        for (Object[] r : rows) out.put((String) r[0], ((Number) r[1]).longValue());
        return out;
    }

    private static Map<String, Object> where(Audience a, StringBuilder sql) {
        Map<String, Object> p = new HashMap<>();
        sql.append("f.deleted = FALSE AND f.blocked_bot = FALSE AND f.lab = FALSE");
        if (a.type() == Audience.Type.SINGLE) {
            sql.append(" AND f.id = :farmerId");
            p.put("farmerId", a.farmerId() == null ? -1L : a.farmerId());
            return p;
        }
        if (a.type() == Audience.Type.ALL) return p;
        if (notEmpty(a.languages())) { sql.append(" AND f.language IN (:langs)"); p.put("langs", a.languages()); }
        if (notEmpty(a.countries())) { sql.append(" AND f.country_code IN (:countries)"); p.put("countries", a.countries()); }
        if (notEmpty(a.crops())) { sql.append(" AND f.main_crop IN (:crops)"); p.put("crops", a.crops().stream().map(Enum::name).toList()); }
        if (notEmpty(a.leadStatuses())) { sql.append(" AND f.lead_status IN (:leads)"); p.put("leads", a.leadStatuses().stream().map(Enum::name).toList()); }
        if (a.hasFields() != null) {
            sql.append(a.hasFields() ? " AND" : " AND NOT").append(" EXISTS (SELECT 1 FROM fields x WHERE x.farmer_id = f.id AND x.deleted = FALSE)");
        }
        if (a.activated() != null) sql.append(a.activated() ? " AND f.activated_at IS NOT NULL" : " AND f.activated_at IS NULL");
        if (a.paid() != null) {
            // " EXISTS" — text block boshidagi umumiy bo'shliqni kesadi: "ANDEXISTS" bo'lib qolardi
            sql.append(a.paid() ? " AND" : " AND NOT").append(" ").append("""
                     EXISTS (SELECT 1 FROM farmer_subscriptions s WHERE s.farmer_id = f.id AND s.status = 'ACTIVE'
                             AND current_date BETWEEN s.valid_from AND s.valid_until)""");
        }
        if (a.registeredFrom() != null) { sql.append(" AND f.created_date >= :from"); p.put("from", a.registeredFrom().atStartOfDay()); }
        if (a.registeredTo() != null) { sql.append(" AND f.created_date < :to"); p.put("to", a.registeredTo().plusDays(1).atStartOfDay()); }
        return p;
    }

    private static boolean notEmpty(List<?> l) {
        return l != null && !l.isEmpty();
    }
}
