package uz.app.starter.rbac.dto;

import java.time.LocalDateTime;

public record PermissionResponse(
        Long id,
        String name,
        String description
) {}
