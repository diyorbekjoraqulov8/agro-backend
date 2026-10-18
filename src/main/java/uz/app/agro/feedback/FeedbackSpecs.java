package uz.app.agro.feedback;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FeedbackSpecs {

    private FeedbackSpecs() {}

    /**
     * @param visibleTo null — hammasi (feedback:manage); aks holda navbat (NEW) + shu xodimning suhbatlari.
     *                  Begona suhbat ro'yxatda ham, id bo'yicha ham ko'rinmaydi (ADR #16 — 404).
     * @param active    true — faqat yopilmaganlar (NEW + OPEN; "Obuna so'rovlari" tabi), status bilan birga ishlatilmaydi
     */
    static Specification<FeedbackEntity> filter(FeedbackStatus status, boolean active, FeedbackKind kind, Long farmerId,
                                                Long assigneeId, Short rating, String q, Long visibleTo) {
        return (root, query, cb) -> {
            // Ro'yxatda fermer ismi kerak — N+1 bo'lmasin (count so'rovida fetch qilinmaydi)
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("farmer", JoinType.INNER);
            }
            List<Predicate> p = new ArrayList<>();
            if (visibleTo != null) {
                p.add(cb.or(cb.equal(root.get("status"), FeedbackStatus.NEW), cb.equal(root.get("assigneeId"), visibleTo)));
            }
            if (status != null)     p.add(cb.equal(root.get("status"), status));
            if (active)             p.add(cb.notEqual(root.get("status"), FeedbackStatus.CLOSED));
            if (kind != null)       p.add(cb.equal(root.get("kind"), kind));
            if (farmerId != null)   p.add(cb.equal(root.get("farmer").get("id"), farmerId));
            if (assigneeId != null) p.add(cb.equal(root.get("assigneeId"), assigneeId));
            if (rating != null)     p.add(cb.equal(root.get("rating"), rating));
            if (q != null && !q.isBlank() && query != null) {
                // % va _ — LIKE metabelgilari; qidiruvda literal bo'lsin
                String like = "%" + q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                // Suhbatning istalgan xabari yoki fermer ismi bo'yicha
                Subquery<Long> sq = query.subquery(Long.class);
                var m = sq.from(FeedbackMessageEntity.class);
                sq.select(m.get("feedbackId")).where(cb.equal(m.get("feedbackId"), root.get("id")),
                        cb.like(cb.lower(m.get("text")), like, '\\'));
                p.add(cb.or(cb.exists(sq), cb.like(cb.lower(root.get("farmer").get("fullName")), like, '\\')));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
