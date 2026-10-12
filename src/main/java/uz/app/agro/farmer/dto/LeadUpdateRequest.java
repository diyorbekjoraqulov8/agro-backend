package uz.app.agro.farmer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.app.agro.farmer.LeadStatus;

public record LeadUpdateRequest(
        @NotNull(message = "leadStatus ko'rsatilishi shart")
        LeadStatus leadStatus,

        @Size(max = 2000, message = "leadNote ko'pi bilan 2000 belgi")
        String leadNote
) {}
