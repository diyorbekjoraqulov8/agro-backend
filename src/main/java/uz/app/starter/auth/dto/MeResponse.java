package uz.app.starter.auth.dto;

import java.util.Set;

public record MeResponse(
        Long id,
        String email,
        Set<String> roles,
        Set<String> permissions
) {}
