package uz.app.agro.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chegirma (V38). O'chirilmaydi — active=false (obunalar discount_id bilan bog'langan, tarix buzilmasin).
 * audiences/plan_ids — vergul bilan ro'yxat (DB check shaklini tekshiradi)
 */
@Entity
@Table(name = "discounts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class DiscountEntity {

    public enum ValueType { PERCENT, FIXED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DiscountKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 8)
    private ValueType valueType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(nullable = false, length = 64)
    private String audiences;

    @Column(name = "plan_ids", nullable = false, length = 255)
    private String planIds = "";

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "updated_by_email", length = 120)
    private String updatedByEmail;

    public Set<DiscountAudience> audienceSet() {
        Set<DiscountAudience> s = EnumSet.noneOf(DiscountAudience.class);
        for (String a : audiences.split(",")) if (!a.isBlank()) s.add(DiscountAudience.valueOf(a.trim()));
        return s;
    }

    public void audienceSet(Set<DiscountAudience> set) {
        this.audiences = set.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    /** Bo'sh — barcha narxli tariflar */
    public Set<Long> planIdSet() {
        return planIds.isBlank() ? Set.of()
                : Arrays.stream(planIds.split(",")).map(String::trim).map(Long::valueOf).collect(Collectors.toSet());
    }

    public void planIdSet(Set<Long> ids) {
        this.planIds = ids == null ? "" : ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean activeOn(LocalDate day) {
        return active && !day.isBefore(startsOn) && (endsOn == null || !day.isAfter(endsOn));
    }

    /** Qat'iy summa — so'mda (UZS tariflarga); foiz — har qanday valyutaga */
    public boolean applicableTo(String currency) {
        return valueType == ValueType.PERCENT || "UZS".equals(currency);
    }

    /** Bir davr narxi chegirma bilan: so'm — butun, boshqa valyuta — tiyin aniqligida; 0 dan past emas */
    public BigDecimal apply(BigDecimal price, String currency) {
        int scale = "UZS".equals(currency) ? 0 : 2;
        BigDecimal r = valueType == ValueType.PERCENT
                ? price.multiply(BigDecimal.valueOf(100).subtract(value)).divide(BigDecimal.valueOf(100), scale, RoundingMode.HALF_UP)
                : price.subtract(value).setScale(scale, RoundingMode.HALF_UP);
        return r.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
