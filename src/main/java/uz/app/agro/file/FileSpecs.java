package uz.app.agro.file;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FileSpecs {

    private FileSpecs() {}

    /** @param type IMAGE / PDF; q — fayl nomi yoki hashId bo'yicha; from/to — yuklangan sana (shu kunlar ham kiradi) */
    static Specification<FileEntity> filter(String q, String type, FileSource source, Long uploadedById,
                                            LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String s = q.trim();
                // % va _ — LIKE metabelgilari; qidiruvda literal bo'lsin
                String like = "%" + s.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("originalName")), like, '\\'), cb.equal(root.get("hashId"), s)));
            }
            if ("IMAGE".equals(type)) p.add(cb.like(root.get("contentType"), "image/%"));
            else if ("PDF".equals(type)) p.add(cb.equal(root.get("contentType"), "application/pdf"));
            if (source != null)       p.add(cb.equal(root.get("source"), source));
            if (uploadedById != null) p.add(cb.equal(root.get("uploadedById"), uploadedById));
            if (from != null)         p.add(cb.greaterThanOrEqualTo(root.get("createdDate"), from.atStartOfDay()));
            if (to != null)           p.add(cb.lessThan(root.get("createdDate"), to.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
