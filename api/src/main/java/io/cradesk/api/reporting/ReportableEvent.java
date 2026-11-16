package io.cradesk.api.reporting;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reportable_events")
public class ReportableEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "finding_id", nullable = false, updatable = false)
    private UUID findingId;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @Column(name = "cve_id", nullable = false, length = 30, updatable = false)
    private String cveId;

    @Column(name = "confirmed_by", updatable = false)
    private UUID confirmedBy;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    // Constructor vacío que JPA necesita para cargar eventos desde la base de datos.
    protected ReportableEvent() {
    }

    // Crea el evento reportable que abre la confirmación de un hallazgo KEV por una persona.
    public ReportableEvent(UUID organizationId, UUID findingId, UUID productVersionId, String cveId,
            UUID confirmedBy, Instant confirmedAt) {
        this.organizationId = organizationId;
        this.findingId = findingId;
        this.productVersionId = productVersionId;
        this.cveId = cveId;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt.truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id del evento.
    public UUID getId() {
        return id;
    }

    // Devuelve el id de la empresa dueña del evento.
    public UUID getOrganizationId() {
        return organizationId;
    }

    // Devuelve el id del hallazgo confirmado que abrió el evento.
    public UUID getFindingId() {
        return findingId;
    }

    // Devuelve el id de la versión de producto afectada.
    public UUID getProductVersionId() {
        return productVersionId;
    }

    // Devuelve el CVE explotado del evento.
    public String getCveId() {
        return cveId;
    }

    // Devuelve quién confirmó el hallazgo.
    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    // Devuelve cuándo se confirmó, que es cuando arrancan los relojes.
    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
