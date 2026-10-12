package uz.app.agro.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uz.app.agro.common.exception.BadRequestException;

import java.util.Set;
import java.util.stream.Collectors;

public final class Pages {

    private static final int MAX_SORT_ORDERS = 3;
    // Spring Data offset > Integer.MAX_VALUE da exception (500) tashlaydi; chuqur OFFSET
    // baribir sekin — undan uzoqqa filtr bilan boriladi
    private static final long MAX_OFFSET = 1_000_000;

    private Pages() {}

    /**
     * `page`/`size`/`sort` mijozdan keladi — 500 o'rniga 400 bo'lsin.
     * Offset chegaralangan (page=99999999 → Spring Data'da 500).
     * `sort` ruxsat etilgan maydonlar ro'yxati bilan cheklanadi.
     * Aks holda noma'lum maydon Spring Data'da 500 bo'lib chiqadi (sort=["id"] — Swagger),
     * yoki LAZY bog'lanish / JSONB bo'yicha qimmat saralash ochiladi.
     */
    public static Pageable guard(Pageable pageable, Set<String> sortable) {
        if (pageable.getOffset() > MAX_OFFSET) {
            throw new BadRequestException("page juda katta: offset (page × size) ko'pi bilan " + MAX_OFFSET
                    + ". Ro'yxatni filtrlar bilan toraytiring");
        }
        Sort sort = pageable.getSort();
        if (sort.stream().count() > MAX_SORT_ORDERS) {
            throw new BadRequestException("sort ko'pi bilan " + MAX_SORT_ORDERS + " ta maydon");
        }
        for (Sort.Order o : sort) {
            if (!sortable.contains(o.getProperty())) {
                throw new BadRequestException("sort maydoni noma'lum: '" + o.getProperty()
                        + "'. Ruxsat etilgan: " + sortable.stream().sorted().collect(Collectors.joining(", "))
                        + " (masalan: sort=createdDate,desc)");
            }
        }
        return pageable;
    }
}
