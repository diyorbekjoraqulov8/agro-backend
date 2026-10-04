package uz.app.agro.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.common.MdcFilter;
import uz.app.agro.rbac.Permission;
import uz.app.agro.rbac.Role;
import uz.app.agro.user.UserRepository;
import uz.app.agro.user.UserEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DbAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SessionService sessionService;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {

        String sid = jwt.getClaimAsString("sid");
        if (sid == null) throw invalid("sid claim yo'q");

        UUID sessionId;
        try {
            sessionId = UUID.fromString(sid);
        } catch (IllegalArgumentException e) {
            throw invalid("sid formati noto'g'ri");
        }

        if (!sessionService.isActive(sessionId)) throw invalid("Sessiya bekor qilingan");

        String email = jwt.getClaimAsString("email");
        if (email == null) throw invalid("email claim yo'q");

        UserEntity user = userRepository.findWithPermissionsByEmail(email)
                .orElseThrow(() -> invalid("Foydalanuvchi topilmadi"));

        if (!user.isActive()) throw invalid("Foydalanuvchi faol emas");

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

        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), sessionId, roles, permissions);
        MDC.put(MdcFilter.USER_ID, String.valueOf(user.getId()));
        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);

    }

    private OAuth2AuthenticationException invalid(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", message, null));
    }
}
