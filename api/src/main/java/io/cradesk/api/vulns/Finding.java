package io.cradesk.api.vulns;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "findings")
public class Finding {

    public static final String VERSION_VULN_UNIQUE = "findings_version_vuln_unique";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vulnerability_id", nullable = false, updatable = false)
    private Vulnerability vulnerability;

    @Column(name = "cve_id", nullable = false, length = 30, updatable = false)
    private String cveId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FindingStatus status;

    @Column(nullable = false)
    private boolean present;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    // Constructor vacío que JPA necesita para cargar hallazgos desde la base de datos.
    protected Finding() {
    }

    // Crea un hallazgo para revisar: una vulnerabilidad KEV presente en una versión de producto.
    public Finding(UUID organizationId, UUID productVersionId, Vulnerability vulnerability, String cveId,
            Instant now) {
        this.organizationId = organizationId;
        this.productVersionId = productVersionId;
        this.vulnerability = vulnerability;
        this.cveId = cveId;
        this.status = FindingStatus.PENDING_REVIEW;
        this.present = true;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.lastSeenAt = createdAt;
    }

    // Registra que el último escaneo volvió a encontrar la vulnerabilidad.
    public void seenAt(Instant now) {
        present = true;
        lastSeenAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    // Registra que el último escaneo ya no encontró la vulnerabilidad, sin borrar el hallazgo.
    public void markAbsent() {
        present = false;
    }

    // Devuelve el id del hallazgo.
    public UUID getId() {
        return id;
    }

    // Devuelve el id de la empresa dueña del hallazgo.
    public UUID getOrganizationId() {
        return organizationId;
    }

    // Devuelve el id de la versión de producto afectada.
    public UUID getProductVersionId() {
        return productVersionId;
    }

    // Devuelve la vulnerabilidad del hallazgo.
    public Vulnerability getVulnerability() {
        return vulnerability;
    }

    // Devuelve el CVE por el que la vulnerabilidad está en KEV.
    public String getCveId() {
        return cveId;
    }

    // Devuelve el estado de revisión del hallazgo.
    public FindingStatus getStatus() {
        return status;
    }

    // Indica si el último escaneo seguía encontrando la vulnerabilidad.
    public boolean isPresent() {
        return present;
    }

    // Devuelve cuándo se creó el hallazgo.
    public Instant getCreatedAt() {
        return createdAt;
    }

    // Devuelve la última vez que un escaneo encontró la vulnerabilidad.
    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
