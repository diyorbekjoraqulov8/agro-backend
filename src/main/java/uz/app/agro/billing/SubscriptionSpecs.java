package uz.app.agro.billing;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class SubscriptionSpecs {

    private SubscriptionSpecs() {}

    /** expiringBefore — bugundan shu sanagacha tugaydigan FAOL obunalar ("eslatish kerak" ro'yxati) */
    static Specification<SubscriptionEntity> filter(SubscriptionEntity.Status status, Long planId,
                                                    LocalDate expiringBefore, LocalDate today) {
        return (root, query, cb) -> {
            // Ro'yxatda plan nomi kerak — N+1 bo'lmasin (count so'rovida fetch qilinmaydi)
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("plan", JoinType.INNER);
            }
            List<Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (planId != null) p.add(cb.equal(root.get("plan").get("id"), planId));
            if (expiringBefore != null) {
                p.add(cb.equal(root.get("status"), SubscriptionEntity.Status.ACTIVE));
                p.add(cb.between(root.get("validUntil"), today, expiringBefore));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
