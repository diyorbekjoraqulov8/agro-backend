package uz.app.agro.analysis;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class AnalysisSpecs {

    private AnalysisSpecs() {}

    static Specification<AnalysisJob> filter(Long fieldId, AnalysisJob.Status status, AnalysisJob.Kind kind) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (fieldId != null) p.add(cb.equal(root.get("field").get("id"), fieldId));
            if (status != null)  p.add(cb.equal(root.get("status"), status));
            if (kind != null)    p.add(cb.equal(root.get("kind"), kind));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
