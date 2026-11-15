package io.cradesk.api.identity;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record TenantPrincipal(UUID organizationId, UUID userId, String email, AccessRole role) {

    // Devuelve la autoridad de Spring Security que corresponde al rol, por ejemplo ROLE_MEMBER.
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
