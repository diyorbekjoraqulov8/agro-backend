package uz.app.agro.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import uz.app.agro.rbac.Permission;
import uz.app.agro.rbac.Role;
import uz.app.agro.user.UserEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Foydalanuvchi → AuthUser + authorities (rollar ROLE_*, permission nomlari). Huquqlar har so'rovda DB'dan (ADR #13):
 * JWT (DbAuthenticationConverter) va support guruhi (Telegram ID) bir xil yig'iladi. roles.permissions yuklangan bo'lsin
 */
public final class UserPrincipals {

    public record Built(AuthUser user, Set<GrantedAuthority> authorities) {}

    private UserPrincipals() {}

    public static Built of(UserEntity user, UUID sessionId) {
        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (Role role : user.getRoles()) {
            roles.add(role.getName());
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            for (Permission p : role.getPermissions()) {
                permissions.add(p.getName());
                authorities.add(new SimpleGrantedAuthority(p.getName()));
            }
        }
        return new Built(new AuthUser(user.getId(), user.getEmail(), sessionId, roles, permissions), authorities);
    }
}
