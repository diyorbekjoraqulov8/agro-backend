package uz.app.starter.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleRequest(

        @NotBlank(message = "Nom bo'sh bo'lmasligi kerak!")
        @Size(max = 255)
        String name,

        String description,
        Set<Long> permissionIds
) {}
