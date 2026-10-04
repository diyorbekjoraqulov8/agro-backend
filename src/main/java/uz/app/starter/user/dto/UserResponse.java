package uz.app.starter.user.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        Set<String> roles,
        boolean active,
        LocalDateTime createdDate
) {}
