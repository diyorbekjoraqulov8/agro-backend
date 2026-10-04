package uz.app.agro.rbac.dto;

import java.util.Set;

public record RoleUpdateRequest(
        String description,
        Set<Long> permissionIds
) {}
