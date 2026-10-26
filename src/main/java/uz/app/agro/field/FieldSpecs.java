package uz.app.agro.field;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FieldSpecs {

    private FieldSpecs() {}

    /** @param lab true — faqat Lab dalalari, false — faqat fermerlarniki, null — hammasi */
    static Specification<FieldEntity> filter(Long farmerId, Crop crop, String country, Boolean lab) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (farmerId != null) p.add(cb.equal(root.get("farmer").get("id"), farmerId));
            if (crop != null)     p.add(cb.equal(root.get("crop"), crop));
            if (country != null || lab != null) {
                var farmer = root.join("farmer");
                if (country != null) p.add(cb.equal(farmer.get("countryCode"), country.toUpperCase(Locale.ROOT)));
                if (lab != null)     p.add(cb.equal(farmer.get("lab"), lab));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
