package uz.app.agro.security;

import java.util.Set;
import java.util.UUID;

public record AuthUser(
        Long id,
        String email,
        UUID sessionId,
        Set<String> roles,
        Set<String> permissions
) {}
