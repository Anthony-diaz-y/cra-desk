package io.cradesk.api.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.catalog.Product;
import io.cradesk.api.catalog.ProductClass;
import io.cradesk.api.catalog.ProductRepository;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.DemoProperties;
import io.cradesk.api.identity.Organization;
import io.cradesk.api.identity.OrganizationRepository;
import io.cradesk.api.identity.UserAccount;
import io.cradesk.api.identity.UserAccountRepository;
import io.cradesk.api.sbom.CycloneDxParser;
import io.cradesk.api.sbom.SbomRepository;
import io.cradesk.api.sbom.SbomService;
import io.cradesk.api.sbom.UploadChannel;

@Component
public class DemoAccountInitializer implements ApplicationRunner {

    static final String SAMPLE_SBOM = "/demo/smart-door-lock-1.1.0.cdx.json";
    static final String LOCK_NAME = "Smart Door Lock";
    static final String LOCK_SBOM_VERSION = "1.1.0";

    private final DemoProperties properties;
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final TransactionTemplate transactions;
    private final CycloneDxParser parser;
    private final SbomService sboms;
    private final SbomRepository sbomRepository;

    // Recibe la configuración de la demo, los repositorios donde se crean sus datos y el gestor de transacciones.
    public DemoAccountInitializer(DemoProperties properties, OrganizationRepository organizations,
            UserAccountRepository users, ProductRepository products, ProductVersionRepository versions,
            TransactionTemplate transactions, CycloneDxParser parser, SbomService sboms,
            SbomRepository sbomRepository) {
        this.properties = properties;
        this.organizations = organizations;
        this.users = users;
        this.products = products;
        this.versions = versions;
        this.transactions = transactions;
        this.parser = parser;
        this.sboms = sboms;
        this.sbomRepository = sbomRepository;
    }

    // Al arrancar la aplicación crea la cuenta demo si la demo está activada.
    @Override
    public void run(ApplicationArguments args) {
        if (properties.enabled()) {
            seed();
        }
    }

    // Crea en una sola transacción los datos de la demo, o completa lo que falte si la demo ya existe.
    public void seed() {
        transactions.executeWithoutResult(status -> {
            Optional<UserAccount> demoUser = users.findByEmail(DemoProperties.EMAIL);
            if (demoUser.isEmpty()) {
                createDemoData();
            } else {
                addMissingSampleSbom(demoUser.get().getOrganization().getId());
            }
        });
    }

    // Agrega el SBOM de ejemplo a la cerradura de la demo si esa versión todavía no tiene uno.
    private void addMissingSampleSbom(UUID organizationId) {
        products.findAllByOrganizationId(organizationId, Sort.unsorted()).stream()
                .filter(p -> p.getName().equals(LOCK_NAME))
                .findFirst()
                .flatMap(lock -> versions.findByProductIdOrderByCreatedAtDescIdDesc(lock.getId()).stream()
                        .filter(v -> v.getVersion().equals(LOCK_SBOM_VERSION))
                        .findFirst())
                .filter(version -> sbomRepository.findByProductVersionId(version.getId()).isEmpty())
                .ifPresent(version -> storeSample(version.getProduct().getId(), version.getId()));
    }

    // Guarda la empresa demo, su usuario sin contraseña y dos productos con sus versiones.
    private void createDemoData() {
        Organization demo = organizations.save(new Organization(DemoProperties.ORGANIZATION_NAME));
        users.save(new UserAccount(demo, DemoProperties.EMAIL, null, AccessRole.DEMO));

        Product lock = products.save(new Product(demo.getId(), LOCK_NAME,
                "Wi-Fi door lock with companion mobile app", ProductClass.IMPORTANT_CLASS_I));
        versions.save(new ProductVersion(lock, "1.0.0", LocalDate.of(2026, 6, 1)));
        ProductVersion lockLatest = versions.save(
                new ProductVersion(lock, LOCK_SBOM_VERSION, LocalDate.of(2026, 10, 1)));
        storeSample(lock.getId(), lockLatest.getId());

        Product gateway = products.save(new Product(demo.getId(), "Home Gateway",
                "Zigbee and Thread home automation hub", ProductClass.DEFAULT));
        versions.save(new ProductVersion(gateway, "2.3.1", LocalDate.of(2026, 9, 15)));
    }

    // Guarda el SBOM de ejemplo como SBOM de esa versión de la cerradura.
    private void storeSample(UUID productId, UUID versionId) {
        byte[] sample = readSample();
        sboms.store(productId, versionId, sample, parser.parse(sample), null, UploadChannel.WEB);
    }

    // Lee el SBOM de ejemplo de la cerradura que viene dentro de la aplicación.
    private static byte[] readSample() {
        try (InputStream in = DemoAccountInitializer.class.getResourceAsStream(SAMPLE_SBOM)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
