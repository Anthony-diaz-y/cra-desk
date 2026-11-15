package io.cradesk.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;
import io.cradesk.api.support.TestTenants.TestTenant;

@IntegrationTest
class ConcurrencyIT {

    @Autowired
    TestTenants tenants;

    @Autowired
    AuthService auth;

    @Autowired
    ApiKeyService apiKeys;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    // Borra claves, empresas y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions CASCADE");
    }

    // Comprueba que diez logins fallidos en paralelo cuentan todos y bloquean la cuenta.
    @Test
    void parallelFailedLoginsStillLockTheAccount() throws Exception {
        TestTenant acme = tenants.create("Acme");
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> attackers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread attacker = new Thread(() -> {
                await(start);
                try {
                    auth.login(new LoginRequest(acme.email(), "wrong-password-123"), new MockHttpServletRequest(),
                            new MockHttpServletResponse());
                } catch (InvalidCredentialsException expected) {
                    return;
                }
            });
            attacker.start();
            attackers.add(attacker);
        }
        start.countDown();
        for (Thread attacker : attackers) {
            attacker.join(30_000);
        }

        assertThat(jdbc.queryForObject("SELECT locked_until FROM users WHERE email = ?", Object.class,
                acme.email())).isNotNull();
    }

    // Comprueba que un uso de la clave en curso no deshace una revocación que se confirmó mientras tanto.
    @Test
    void inFlightKeyUseDoesNotUndoRevocation() throws Exception {
        TestTenant acme = tenants.create("Acme");
        signIn(acme);
        ApiKeyCreatedResponse key = apiKeys.create(new CreateApiKeyRequest("ci"));
        CountDownLatch keyLoaded = new CountDownLatch(1);
        CountDownLatch revoked = new CountDownLatch(1);

        Thread cli = new Thread(() -> transactions.executeWithoutResult(status -> {
            apiKeys.authenticate(key.key());
            keyLoaded.countDown();
            await(revoked);
        }));
        cli.start();
        await(keyLoaded);
        apiKeys.revoke(key.id());
        revoked.countDown();
        cli.join(30_000);

        assertThat(jdbc.queryForObject("SELECT revoked_at FROM api_keys WHERE id = ?", Object.class, key.id()))
                .isNotNull();
        assertThat(apiKeys.authenticate(key.key())).isEmpty();
    }

    // Autentica el hilo actual como el miembro de esa empresa.
    private static void signIn(TestTenant tenant) {
        TenantPrincipal principal = new TenantPrincipal(tenant.organizationId(), tenant.userId(), tenant.email(),
                AccessRole.MEMBER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
    }

    // Espera a que el contador llegue a cero, como máximo diez segundos.
    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
