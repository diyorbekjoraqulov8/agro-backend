package uz.app.agro.field;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FieldSpecs {

    private FieldSpecs() {}

    static Specification<FieldEntity> filter(Long farmerId, Crop crop, String country) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (farmerId != null) p.add(cb.equal(root.get("farmer").get("id"), farmerId));
            if (crop != null)     p.add(cb.equal(root.get("crop"), crop));
            if (country != null)  p.add(cb.equal(root.join("farmer").get("countryCode"),
                                                 country.toUpperCase(Locale.ROOT)));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
