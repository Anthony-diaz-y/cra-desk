package io.cradesk.api.identity;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenant {

    // Devuelve el principal de la petición actual o lanza error si no hay nadie autenticado.
    public TenantPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("No authenticated tenant in this request");
    }

    // Devuelve el id de la empresa de la petición actual.
    public UUID organizationId() {
        return principal().organizationId();
    }
}
