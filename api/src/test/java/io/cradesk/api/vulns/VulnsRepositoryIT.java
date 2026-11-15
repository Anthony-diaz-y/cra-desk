package io.cradesk.api.vulns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.catalog.Product;
import io.cradesk.api.catalog.ProductClass;
import io.cradesk.api.catalog.ProductRepository;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.common.Constraints;
import io.cradesk.api.identity.Organization;
import io.cradesk.api.identity.OrganizationRepository;
import io.cradesk.api.sbom.Sbom;
import io.cradesk.api.sbom.SbomComponent;
import io.cradesk.api.sbom.SbomComponentRepository;
import io.cradesk.api.sbom.SbomRepository;
import io.cradesk.api.sbom.UploadChannel;
import io.cradesk.api.support.IntegrationTest;

@IntegrationTest
@Transactional
class VulnsRepositoryIT {

    static final Instant T1 = Instant.parse("2026-11-01T10:00:00Z");
    static final Instant T2 = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductVersionRepository versions;

    @Autowired
    SbomRepository sboms;

    @Autowired
    SbomComponentRepository components;

    @Autowired
    VulnerabilityRepository vulnerabilities;

    @Autowired
    VulnerabilityMatchRepository matches;

    @Autowired
    FindingRepository findings;

    @Autowired
    VersionScanRepository scans;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    Organization acme;
    Product lock;
    ProductVersion version;
    SbomComponent log4j;

    // Crea una empresa con un producto, una versión y un SBOM con el componente log4j.
    @BeforeEach
    void createSbom() {
        acme = organizations.saveAndFlush(new Organization("Acme"));
        lock = products.saveAndFlush(new Product(acme.getId(), "Smart Lock", null, ProductClass.DEFAULT));
        version = versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null));
        Sbom sbom = sboms.saveAndFlush(new Sbom(version.getId(), "1.5", null, 1, "a".repeat(64), 10, 1, 0, "{}",
                null, UploadChannel.WEB));
        log4j = components.saveAndFlush(new SbomComponent(sbom, null, "library", "org.apache.logging.log4j",
                "log4j-core", "2.14.1", "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"));
    }

    // Comprueba que una vulnerabilidad se guarda y se recupera con su lista de alias.
    @Test
    void savesVulnerabilityWithAliases() {
        vulnerabilities.saveAndFlush(log4shell());
        entityManager.clear();

        Vulnerability found = vulnerabilities.findById("GHSA-jfh8-c2jp-5v3q").orElseThrow();

        assertThat(found.getAliases()).containsExactly("CVE-2021-44228");
        assertThat(found.getSeverity()).isEqualTo("CRITICAL");
        assertThat(found.getModified()).isEqualTo(T1);
    }

    // Comprueba que al borrar el SBOM se borran sus componentes y las coincidencias de esos componentes.
    @Test
    void matchesAreDeletedWithTheirComponent() {
        Vulnerability vulnerability = vulnerabilities.saveAndFlush(log4shell());
        matches.saveAndFlush(new VulnerabilityMatch(version.getId(), log4j, vulnerability, true));

        jdbc.update("DELETE FROM sboms");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM vulnerability_matches", Integer.class)).isZero();
    }

    // Comprueba que no puede haber dos hallazgos de la misma vulnerabilidad en la misma versión.
    @Test
    void oneFindingPerVersionAndVulnerability() {
        Vulnerability vulnerability = vulnerabilities.saveAndFlush(log4shell());
        findings.saveAndFlush(new Finding(acme.getId(), version.getId(), vulnerability, "CVE-2021-44228", T1));

        DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> findings.saveAndFlush(
                        new Finding(acme.getId(), version.getId(), vulnerability, "CVE-2021-44228", T2)));

        assertThat(Constraints.isViolated(e, Finding.VERSION_VULN_UNIQUE)).isTrue();
    }

    // Comprueba que el siguiente escaneo pendiente es el que se pidió primero.
    @Test
    void nextPendingScanIsTheOldest() {
        ProductVersion second = versions.saveAndFlush(new ProductVersion(lock, "1.1.0", null));
        VersionScan newer = new VersionScan(version.getId());
        newer.request(T2);
        VersionScan older = new VersionScan(second.getId());
        older.request(T1);
        scans.saveAllAndFlush(List.of(newer, older));

        VersionScan next = scans.findFirstByStatusOrderByRequestedAtAsc(ScanStatus.PENDING).orElseThrow();

        assertThat(next.getProductVersionId()).isEqualTo(second.getId());
    }

    // Crea la vulnerabilidad Log4Shell tal como la describe OSV.
    private static Vulnerability log4shell() {
        return new Vulnerability("GHSA-jfh8-c2jp-5v3q", "Remote code injection in Log4j", "CRITICAL",
                List.of("CVE-2021-44228"), T1, T1, T1);
    }
}
