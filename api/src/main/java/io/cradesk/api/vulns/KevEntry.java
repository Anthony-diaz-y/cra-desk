package io.cradesk.api.vulns;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kev_entries")
public class KevEntry {

    @Id
    @Column(name = "cve_id", length = 30)
    private String cveId;

    @Column(name = "vendor_project", length = 200)
    private String vendorProject;

    @Column(length = 200)
    private String product;

    @Column(name = "vulnerability_name", length = 500)
    private String vulnerabilityName;

    @Column(name = "date_added", nullable = false)
    private LocalDate dateAdded;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "known_ransomware", length = 20)
    private String knownRansomware;

    @Column(name = "short_description", length = 2000)
    private String shortDescription;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    // Constructor vacío que JPA necesita para cargar entradas del catálogo desde la base de datos.
    protected KevEntry() {
    }

    // Crea una entrada del catálogo KEV de CISA con la fecha en que se descargó.
    public KevEntry(String cveId, String vendorProject, String product, String vulnerabilityName,
            LocalDate dateAdded, LocalDate dueDate, String knownRansomware, String shortDescription,
            Instant fetchedAt) {
        this.cveId = cveId;
        this.vendorProject = cut(vendorProject, 200);
        this.product = cut(product, 200);
        this.vulnerabilityName = cut(vulnerabilityName, 500);
        this.dateAdded = dateAdded;
        this.dueDate = dueDate;
        this.knownRansomware = cut(knownRansomware, 20);
        this.shortDescription = cut(shortDescription, 2000);
        this.fetchedAt = fetchedAt.truncatedTo(ChronoUnit.MICROS);
    }

    // Recorta un texto al largo máximo de su columna.
    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    // Devuelve el CVE de la entrada.
    public String getCveId() {
        return cveId;
    }

    // Devuelve el fabricante o proyecto afectado.
    public String getVendorProject() {
        return vendorProject;
    }

    // Devuelve el producto afectado.
    public String getProduct() {
        return product;
    }

    // Devuelve el nombre de la vulnerabilidad según CISA.
    public String getVulnerabilityName() {
        return vulnerabilityName;
    }

    // Devuelve la fecha en que CISA agregó la vulnerabilidad al catálogo.
    public LocalDate getDateAdded() {
        return dateAdded;
    }

    // Devuelve la fecha límite que CISA fija a las agencias de EE. UU. para corregirla.
    public LocalDate getDueDate() {
        return dueDate;
    }

    // Devuelve si se sabe que se usa en campañas de ransomware (Known o Unknown).
    public String getKnownRansomware() {
        return knownRansomware;
    }

    // Devuelve la descripción corta de CISA.
    public String getShortDescription() {
        return shortDescription;
    }

    // Devuelve cuándo se descargó esta entrada.
    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
