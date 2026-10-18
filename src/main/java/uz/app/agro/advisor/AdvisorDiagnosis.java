package uz.app.agro.advisor;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Claude javobi — structured output (SDK sxemani shu record'dan yasaydi, javob shu tipda keladi).
 * Matn maydonlari fermer tilida. Dori nomi va dozasi YO'Q — "agronom bilan maslahatlashing" (system prompt).
 * Jackson annotatsiyalari com.fasterxml.jackson.annotation — Jackson 2 (SDK) va 3 (loyiha) uchun umumiy paket.
 */
public record AdvisorDiagnosis(
        @JsonPropertyDescription("Main finding category") Category category,
        @JsonPropertyDescription("1-2 sentence plain-language summary for the farmer") String summary,
        @JsonPropertyDescription("Up to 3 likely causes, most likely first; empty if HEALTHY, UNCLEAR or NOT_PLANT") List<Cause> causes,
        @JsonPropertyDescription("What the farmer should check in the field to confirm (2-4 short items)") List<String> checks,
        @JsonPropertyDescription("Safe first steps that need no pesticide or fertilizer product (2-4 short items)") List<String> actions,
        @JsonPropertyDescription("True if the problem can spread or cause significant loss within days") boolean urgent,
        @JsonPropertyDescription("If UNCLEAR or NOT_PLANT: how to take a better photo; otherwise empty string") String photoAdvice
) {
    public enum Category { DISEASE, PEST, NUTRIENT, WATER, ABIOTIC, HEALTHY, UNCLEAR, NOT_PLANT }

    public record Cause(
            @JsonPropertyDescription("Name of the disease, pest or disorder in the farmer's language") String name,
            @JsonPropertyDescription("Confidence 0-100") int confidencePct,
            @JsonPropertyDescription("Which visible signs in the photo support it") String evidence
    ) {}
}
