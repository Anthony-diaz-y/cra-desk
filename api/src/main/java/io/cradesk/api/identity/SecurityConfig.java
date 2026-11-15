package io.cradesk.api.identity;

import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableConfigurationProperties({ AdminProperties.class, DemoProperties.class })
public class SecurityConfig {

    // Define las reglas de acceso, la sesión, el CSRF y las respuestas 401 y 403 de toda la API.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAuthHandlers problems,
            SecurityContextRepository securityContextRepository, AdminProperties adminProperties,
            ApiKeyService apiKeys) throws Exception {
        http
                .addFilterBefore(new AdminTokenFilter(adminProperties, problems), AuthorizationFilter.class)
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys, problems), AuthorizationFilter.class)
                .securityContext(c -> c.securityContextRepository(securityContextRepository))
                .csrf(c -> c.spa().ignoringRequestMatchers(SecurityConfig::carriesTokenHeader))
                .httpBasic(c -> c.disable())
                .formLogin(c -> c.disable())
                .requestCache(c -> c.disable())
                .logout(c -> c.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(c -> c.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/demo",
                                "/api/auth/accept-invitation", "/api/access-requests").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/api-keys/**").hasRole("MEMBER")
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("MEMBER", "DEMO", "CLI")
                        .requestMatchers("/api/**").hasAnyRole("MEMBER", "CLI")
                        .anyRequest().denyAll());
        return http.build();
    }

    // Guarda el contexto de seguridad en la sesión HTTP para que el login persista entre peticiones.
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // Codifica contraseñas con BCrypt dejando el prefijo del algoritmo en el hash.
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // Reloj del sistema en UTC para poder fijar la hora en los tests.
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // Indica si la petición usa clave de API, token de admin o la ruta de admin, que no usan cookies ni necesitan CSRF.
    static boolean carriesTokenHeader(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return (authorization != null && authorization.startsWith("Bearer "))
                || request.getHeader("X-Admin-Token") != null
                || request.getRequestURI().startsWith("/api/admin/");
    }
}
