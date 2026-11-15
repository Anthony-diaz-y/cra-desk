package io.cradesk.api.sbom;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "sboms")
public class Sbom {

    public static final String VERSION_UNIQUE = "sboms_version_unique";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @Column(name = "spec_version", nullable = false, length = 8)
    private String specVersion;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "bom_version")
    private Integer bomVersion;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "component_count", nullable = false)
    private int componentCount;

    @Column(name = "components_without_purl", nullable = false)
    private int componentsWithoutPurl;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String document;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploaded_via", nullable = false, length = 8)
    private UploadChannel uploadedVia;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    // Constructor vacío que JPA necesita para cargar SBOM desde la base de datos.
    protected Sbom() {
    }

    // Crea el SBOM de una versión con sus datos, el documento original y quién lo subió y por dónde.
    public Sbom(UUID productVersionId, String specVersion, String serialNumber, Integer bomVersion, String sha256,
            int sizeBytes, int componentCount, int componentsWithoutPurl, String document, UUID uploadedBy,
            UploadChannel uploadedVia) {
        this.productVersionId = productVersionId;
        this.specVersion = specVersion;
        this.serialNumber = serialNumber;
        this.bomVersion = bomVersion;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.componentCount = componentCount;
        this.componentsWithoutPurl = componentsWithoutPurl;
        this.document = document;
        this.uploadedBy = uploadedBy;
        this.uploadedVia = uploadedVia;
    }

    // Fija la fecha de subida antes de guardar por primera vez.
    @PrePersist
    void onCreate() {
        uploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // Devuelve el id del SBOM.
    public UUID getId() {
        return id;
    }

    // Devuelve el id de la versión de producto a la que pertenece el SBOM.
    public UUID getProductVersionId() {
        return productVersionId;
    }

    // Devuelve la versión de la especificación CycloneDX del documento, por ejemplo 1.5.
    public String getSpecVersion() {
        return specVersion;
    }

    // Devuelve el número de serie del documento, o null si no lo trae.
    public String getSerialNumber() {
        return serialNumber;
    }

    // Devuelve la versión del documento SBOM, o null si no la trae.
    public Integer getBomVersion() {
        return bomVersion;
    }

    // Devuelve el SHA-256 del documento original.
    public String getSha256() {
        return sha256;
    }

    // Devuelve el tamaño del documento original en bytes.
    public int getSizeBytes() {
        return sizeBytes;
    }

    // Devuelve cuántos componentes se extrajeron.
    public int getComponentCount() {
        return componentCount;
    }

    // Devuelve cuántos componentes no traen purl.
    public int getComponentsWithoutPurl() {
        return componentsWithoutPurl;
    }

    // Devuelve el documento original tal como se subió.
    public String getDocument() {
        return document;
    }

    // Devuelve el id del usuario que lo subió, o null si lo creó el sistema.
    public UUID getUploadedBy() {
        return uploadedBy;
    }

    // Devuelve por dónde se subió: web o CLI.
    public UploadChannel getUploadedVia() {
        return uploadedVia;
    }

    // Devuelve cuándo se subió.
    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
