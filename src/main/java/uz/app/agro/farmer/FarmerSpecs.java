package uz.app.agro.farmer;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import uz.app.agro.field.Crop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FarmerSpecs {

    private FarmerSpecs() {}

    static Specification<FarmerEntity> filter(String country, LeadStatus leadStatus, String source,
                                              Crop crop, String q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (country != null)    p.add(cb.equal(root.get("countryCode"), country.toUpperCase(Locale.ROOT)));
            if (leadStatus != null) p.add(cb.equal(root.get("leadStatus"), leadStatus));
            if (source != null)     p.add(cb.equal(root.get("source"), source));
            if (crop != null)       p.add(cb.equal(root.get("mainCrop"), crop));
            if (q != null && !q.isBlank()) {
                // % va _ — LIKE metabelgilari; foydalanuvchi qidiruvida literal bo'lsin
                String like = "%" + q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("telegramUsername")), like, '\\'),
                        cb.like(root.get("phone"), like, '\\')));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
