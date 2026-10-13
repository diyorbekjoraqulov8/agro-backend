package uz.app.agro.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.app.agro.farmer.LeadStatus;
import uz.app.agro.field.Crop;

import java.time.LocalDate;
import java.util.List;

/**
 * Kimga: ALL — hammaga; FILTER — shartlar (bo'sh maydon — cheklov yo'q, hammasi VA bilan); SINGLE — bitta fermer.
 * Botni bloklaganlar va o'chirilganlar hech qachon kirmaydi.
 */
public record Audience(
        @NotNull Type type,
        Long farmerId,
        @Size(max = 3) List<@NotNull String> languages,
        @Size(max = 50) List<@NotNull String> countries,
        Boolean paid,
        @Size(max = 20) List<@NotNull Crop> crops,
        Boolean hasFields,
        Boolean activated,
        LocalDate registeredFrom,
        LocalDate registeredTo,
        @Size(max = 10) List<@NotNull LeadStatus> leadStatuses
) {
    public enum Type { ALL, FILTER, SINGLE }
}
