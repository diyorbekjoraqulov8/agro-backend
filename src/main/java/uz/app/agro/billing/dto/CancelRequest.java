package uz.app.agro.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRequest(
        @NotBlank(message = "reason ko'rsatilishi shart") @Size(max = 500, message = "reason ko'pi bilan 500 belgi")
        String reason
) {}
