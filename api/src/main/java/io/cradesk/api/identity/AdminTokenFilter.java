package io.cradesk.api.identity;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Admin-Token";

    private final AdminProperties properties;
    private final ProblemDetailAuthHandlers problems;

    // Recibe la configuración del token de admin y el escritor de respuestas de error.
    public AdminTokenFilter(AdminProperties properties, ProblemDetailAuthHandlers problems) {
        this.properties = properties;
        this.problems = problems;
    }

    // Si la petición trae X-Admin-Token válido la autentica como admin; si es inválido responde 401.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!properties.enabled() || !SecretTokens.constantTimeEquals(provided, properties.token())) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid admin token");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("platform-admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
