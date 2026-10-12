package uz.app.agro.onboarding;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "onboarding_steps")
@Getter
@Setter
public class OnboardingStepEntity {

    /** O'rnatilgan kodlar — o'chirilmaydi; FINISH har doim oxirida */
    public static final String WELCOME = "WELCOME", FINISH = "FINISH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, updatable = false)
    private String code;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private StepKind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable = false)
    private StepTarget target;

    /** til → matn (uz majburiy; ru/en bo'sh bo'lsa uz) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> texts = new HashMap<>();

    /** Maxsus savol variantlari: [{value, labels: {uz, ru, en}}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> options = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private boolean system;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_by_email", length = 120)
    private String updatedByEmail;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "updated_date", nullable = false)
    private LocalDateTime updatedDate = LocalDateTime.now();
}
