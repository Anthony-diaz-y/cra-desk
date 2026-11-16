package io.cradesk.api.vulns;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "version_scans")
public class VersionScan {

    static final int MAX_ERROR_LENGTH = 500;

    @Id
    @Column(name = "product_version_id")
    private UUID productVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScanStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String error;

    @Column(name = "components_scanned")
    private Integer componentsScanned;

    @Column(name = "components_not_scanned")
    private Integer componentsNotScanned;

    // Constructor vacío que JPA necesita para cargar escaneos desde la base de datos.
    protected VersionScan() {
    }

    // Crea el registro de escaneo de una versión de producto, todavía sin pedir.
    public VersionScan(UUID productVersionId) {
        this.productVersionId = productVersionId;
    }

    // Deja el escaneo pendiente desde ese instante.
    public void request(Instant now) {
        status = ScanStatus.PENDING;
        requestedAt = micros(now);
    }

    // Vuelve a dejar pendiente un escaneo que estaba en curso, por ejemplo tras un reinicio o un SBOM reemplazado.
    public void requeueIfRunning(Instant now) {
        if (status == ScanStatus.RUNNING) {
            request(now);
        }
    }

    // Marca el escaneo como en curso desde ese instante.
    public void start(Instant now) {
        status = ScanStatus.RUNNING;
        startedAt = micros(now);
        error = null;
    }

    // Marca el escaneo como terminado con sus conteos; si se volvió a pedir mientras corría, queda pendiente.
    public void finish(Instant now, int scanned, int notScanned) {
        if (status != ScanStatus.PENDING) {
            status = ScanStatus.DONE;
        }
        finishedAt = micros(now);
        componentsScanned = scanned;
        componentsNotScanned = notScanned;
        error = null;
    }

    // Marca el escaneo como fallido con el motivo; si se volvió a pedir mientras corría, queda pendiente.
    public void fail(Instant now, String reason) {
        if (status != ScanStatus.PENDING) {
            status = ScanStatus.FAILED;
        }
        finishedAt = micros(now);
        String text = reason == null ? "Unknown error" : reason;
        error = text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    // Recorta un instante a microsegundos, la precisión de PostgreSQL.
    private static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id de la versión escaneada.
    public UUID getProductVersionId() {
        return productVersionId;
    }

    // Devuelve el estado del escaneo.
    public ScanStatus getStatus() {
        return status;
    }

    // Devuelve cuándo se pidió el escaneo.
    public Instant getRequestedAt() {
        return requestedAt;
    }

    // Devuelve cuándo empezó el último escaneo.
    public Instant getStartedAt() {
        return startedAt;
    }

    // Devuelve cuándo terminó el último escaneo, bien o con error.
    public Instant getFinishedAt() {
        return finishedAt;
    }

    // Devuelve el motivo del último fallo, o null.
    public String getError() {
        return error;
    }

    // Devuelve cuántos componentes se escanearon en el último escaneo terminado.
    public Integer getComponentsScanned() {
        return componentsScanned;
    }

    // Devuelve cuántos componentes no se pudieron escanear por no tener purl con versión.
    public Integer getComponentsNotScanned() {
        return componentsNotScanned;
    }
}
