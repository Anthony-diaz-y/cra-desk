package io.cradesk.api.identity;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String BEARER = "Bearer ";

    private final ApiKeyService keys;
    private final ProblemDetailAuthHandlers problems;

    // Recibe el servicio que valida claves y el escritor de respuestas de error.
    public ApiKeyAuthenticationFilter(ApiKeyService keys, ProblemDetailAuthHandlers problems) {
        this.keys = keys;
        this.problems = problems;
    }

    // Si la petición trae Authorization: Bearer valida la clave; si es válida autentica como CLI y si no responde 401.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<TenantPrincipal> principal = keys.authenticate(authorization.substring(BEARER.length()).strip());
        if (principal.isEmpty()) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid API key");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal.get(), null,
                principal.get().authorities()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
