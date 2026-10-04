package uz.app.starter.rbac.dto;

import java.time.LocalDateTime;

public record RoleResponse(
        Long id,
        String name,
        String description,
        boolean systemRole,
        LocalDateTime createdDate
){}