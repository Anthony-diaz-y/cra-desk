package io.cradesk.api.reporting;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiQuota {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    // Recibe el acceso JDBC y el reloj.
    public AiQuota(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // Devuelve el día UTC actual, al que se cargan los borradores con IA.
    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    // Suma un borrador con IA a ese día de la empresa si no superó el límite, en una sola sentencia atómica.
    public boolean tryConsume(UUID organizationId, int dailyLimit, LocalDate day) {
        if (dailyLimit <= 0) {
            return false;
        }
        int updated = jdbc.update("""
                INSERT INTO ai_usage (organization_id, day, count) VALUES (?, ?, 1)
                ON CONFLICT (organization_id, day)
                DO UPDATE SET count = ai_usage.count + 1 WHERE ai_usage.count < ?
                """, organizationId, day, dailyLimit);
        return updated == 1;
    }

    // Devuelve a ese día de la empresa la unidad de un borrador con IA que no se pudo generar.
    public void refund(UUID organizationId, LocalDate day) {
        jdbc.update("UPDATE ai_usage SET count = count - 1 WHERE organization_id = ? AND day = ? AND count > 0",
                organizationId, day);
    }
}
