package io.cradesk.api.sbom;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sbom_components")
public class SbomComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sbom_id", nullable = false, updatable = false)
    private Sbom sbom;

    @Column(name = "bom_ref", length = 1024)
    private String bomRef;

    @Column(length = 32)
    private String type;

    @Column(name = "group_name", length = 512)
    private String group;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(length = 256)
    private String version;

    @Column(length = 2048)
    private String purl;

    // Constructor vacío que JPA necesita para cargar componentes desde la base de datos.
    protected SbomComponent() {
    }

    // Crea un componente del SBOM con sus datos de identificación.
    public SbomComponent(Sbom sbom, String bomRef, String type, String group, String name, String version,
            String purl) {
        this.sbom = sbom;
        this.bomRef = bomRef;
        this.type = type;
        this.group = group;
        this.name = name;
        this.version = version;
        this.purl = purl;
    }

    // Devuelve el id del componente.
    public UUID getId() {
        return id;
    }

    // Devuelve el SBOM al que pertenece el componente.
    public Sbom getSbom() {
        return sbom;
    }

    // Devuelve la referencia interna del componente dentro del documento (bom-ref).
    public String getBomRef() {
        return bomRef;
    }

    // Devuelve el tipo del componente, por ejemplo library o firmware.
    public String getType() {
        return type;
    }

    // Devuelve el grupo del componente, por ejemplo el groupId de Maven.
    public String getGroup() {
        return group;
    }

    // Devuelve el nombre del componente.
    public String getName() {
        return name;
    }

    // Devuelve la versión del componente.
    public String getVersion() {
        return version;
    }

    // Devuelve el package URL del componente, o null si no lo trae.
    public String getPurl() {
        return purl;
    }
}
