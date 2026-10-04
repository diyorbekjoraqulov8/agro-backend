package uz.app.agro.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionRequest (
        @NotBlank(message = "Nomi bo'sh bo'lmasligi kerak!")
        @Size(max = 255)
        String name,

        String description
) {}
