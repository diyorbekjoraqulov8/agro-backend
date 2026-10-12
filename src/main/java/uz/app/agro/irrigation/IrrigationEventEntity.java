package uz.app.agro.irrigation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Sug'orish tarixi — o'chirilmaydi (audit_logs mantiqi)
@Entity
@Table(name = "irrigation_events")
@Getter
@Setter
public class IrrigationEventEntity {

    public enum Source { FARMER, SETUP, STAFF }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_id", nullable = false, updatable = false)
    private Long fieldId;

    @Column(name = "irrigated_on", nullable = false, updatable = false)
    private LocalDate irrigatedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Source source;

    @Column(name = "created_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdDate;
}
