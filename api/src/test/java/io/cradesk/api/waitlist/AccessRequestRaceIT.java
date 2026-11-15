package io.cradesk.api.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.support.IntegrationTest;

@IntegrationTest
class AccessRequestRaceIT {

    @Autowired
    AccessRequestService service;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    // Vacía la lista de espera después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE access_requests");
    }

    // Comprueba que dos envíos simultáneos del mismo email (doble clic) terminan bien y guardan una sola fila.
    @Test
    void simultaneousDuplicateSubmitDoesNotFail() throws Exception {
        CountDownLatch firstInserted = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread first = new Thread(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO access_requests (id, email, created_at)
                    VALUES (gen_random_uuid(), 'race@example.com', now())
                    """);
            firstInserted.countDown();
            sleep(500);
        }));
        Thread second = new Thread(() -> {
            await(firstInserted);
            try {
                service.submit(new AccessRequestForm("race@example.com", null, null));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        first.start();
        second.start();
        first.join(30_000);
        second.join(30_000);

        assertThat(error.get()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_requests", Integer.class)).isEqualTo(1);
    }

    // Espera a que el contador llegue a cero, como máximo diez segundos.
    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Duerme el hilo actual esa cantidad de milisegundos.
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
