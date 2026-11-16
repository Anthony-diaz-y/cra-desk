package io.cradesk.api.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.audit.AuditService;
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
import io.cradesk.api.reporting.Report;
import io.cradesk.api.reporting.ReportRepository;
import io.cradesk.api.reporting.ReportType;
import io.cradesk.api.reporting.ReportableEvent;
import io.cradesk.api.reporting.ReportableEventRepository;
import io.cradesk.api.reporting.draft.DraftContent;
import io.cradesk.api.sbom.CycloneDxParser;
import io.cradesk.api.sbom.SbomRepository;
import io.cradesk.api.sbom.SbomService;
import io.cradesk.api.sbom.UploadChannel;
import io.cradesk.api.vulns.Finding;
import io.cradesk.api.vulns.FindingRepository;
import io.cradesk.api.vulns.Vulnerability;
import io.cradesk.api.vulns.VulnerabilityRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DemoAccountInitializer implements ApplicationRunner {

    static final String SAMPLE_SBOM = "/demo/smart-door-lock-1.1.0.cdx.json";
    static final String LOCK_NAME = "Smart Door Lock";
    static final String LOCK_SBOM_VERSION = "1.1.0";
    static final String SAMPLE_DRAFTS = "/demo/log4shell-drafts.json";
    static final String LOG4SHELL_CVE = "CVE-2021-44228";
    static final String LOG4SHELL_ID = "GHSA-jfh8-c2jp-5v3q";
    static final String SAMPLE_ENISA_REFERENCE = "ENISA-DEMO-0001";
    static final Duration CONFIRMED_AGO = Duration.ofHours(30);

    private final DemoProperties properties;
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final TransactionTemplate transactions;
    private final CycloneDxParser parser;
    private final SbomService sboms;
    private final SbomRepository sbomRepository;
    private final VulnerabilityRepository vulnerabilities;
    private final FindingRepository findings;
    private final ReportableEventRepository events;
    private final ReportRepository reports;
    private final AuditService audit;
    private final JsonMapper json;
    private final Clock clock;

    // Recibe la configuración de la demo, los repositorios donde se crean sus datos, la auditoría, JSON y el reloj.
    public DemoAccountInitializer(DemoProperties properties, OrganizationRepository organizations,
            UserAccountRepository users, ProductRepository products, ProductVersionRepository versions,
            TransactionTemplate transactions, CycloneDxParser parser, SbomService sboms,
            SbomRepository sbomRepository, VulnerabilityRepository vulnerabilities, FindingRepository findings,
            ReportableEventRepository events, ReportRepository reports, AuditService audit, JsonMapper json,
            Clock clock) {
        this.properties = properties;
        this.organizations = organizations;
        this.users = users;
        this.products = products;
        this.versions = versions;
        this.transactions = transactions;
        this.parser = parser;
        this.sboms = sboms;
        this.sbomRepository = sbomRepository;
        this.vulnerabilities = vulnerabilities;
        this.findings = findings;
        this.events = events;
        this.reports = reports;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
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
                addMissingSampleEvent(demoUser.get());
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

    // Agrega el evento Log4Shell de ejemplo si la empresa demo todavía no tiene ningún evento.
    private void addMissingSampleEvent(UserAccount demoUser) {
        UUID organizationId = demoUser.getOrganization().getId();
        if (!events.findByOrganizationIdOrderByConfirmedAtDesc(organizationId).isEmpty()) {
            return;
        }
        products.findAllByOrganizationId(organizationId, Sort.unsorted()).stream()
                .filter(p -> p.getName().equals(LOCK_NAME))
                .findFirst()
                .flatMap(lock -> versions.findByProductIdOrderByCreatedAtDescIdDesc(lock.getId()).stream()
                        .filter(v -> v.getVersion().equals(LOCK_SBOM_VERSION))
                        .findFirst())
                .ifPresent(version -> createSampleEvent(demoUser, version));
    }

    // Guarda la empresa demo, su usuario sin contraseña y dos productos con sus versiones.
    private void createDemoData() {
        Organization demo = organizations.save(new Organization(DemoProperties.ORGANIZATION_NAME));
        UserAccount demoUser = users.save(new UserAccount(demo, DemoProperties.EMAIL, null, AccessRole.DEMO));

        Product lock = products.save(new Product(demo.getId(), LOCK_NAME,
                "Wi-Fi door lock with companion mobile app", ProductClass.IMPORTANT_CLASS_I));
        versions.save(new ProductVersion(lock, "1.0.0", LocalDate.of(2026, 6, 1)));
        ProductVersion lockLatest = versions.save(
                new ProductVersion(lock, LOCK_SBOM_VERSION, LocalDate.of(2026, 10, 1)));
        storeSample(lock.getId(), lockLatest.getId());
        createSampleEvent(demoUser, lockLatest);

        Product gateway = products.save(new Product(demo.getId(), "Home Gateway",
                "Zigbee and Thread home automation hub", ProductClass.DEFAULT));
        versions.save(new ProductVersion(gateway, "2.3.1", LocalDate.of(2026, 9, 15)));
    }

    // Guarda el SBOM de ejemplo como SBOM de esa versión de la cerradura.
    private void storeSample(UUID productId, UUID versionId) {
        byte[] sample = readSample();
        sboms.store(productId, versionId, sample, parser.parse(sample), null, UploadChannel.WEB);
    }

    // Confirma un hallazgo Log4Shell de hace 30 horas con sus tres borradores y el aviso temprano ya enviado.
    private void createSampleEvent(UserAccount demoUser, ProductVersion version) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant confirmedAt = now.minus(CONFIRMED_AGO);
        UUID organizationId = demoUser.getOrganization().getId();
        UUID userId = demoUser.getId();
        Vulnerability vulnerability = vulnerabilities.findById(LOG4SHELL_ID).orElseGet(() -> vulnerabilities.save(
                new Vulnerability(LOG4SHELL_ID, "Remote code injection in Log4j", "CRITICAL",
                        List.of(LOG4SHELL_CVE), Instant.parse("2021-12-10T00:00:00Z"), Instant.EPOCH, now)));
        Finding finding = findings.findByProductVersionId(version.getId()).stream()
                .filter(f -> f.getCveId().equals(LOG4SHELL_CVE))
                .findFirst()
                .orElseGet(() -> findings.save(new Finding(organizationId, version.getId(), vulnerability,
                        LOG4SHELL_CVE, confirmedAt)));
        if (!finding.isPendingReview()) {
            return;
        }
        finding.confirm(confirmedAt, userId);
        ReportableEvent event = events.save(new ReportableEvent(organizationId, finding.getId(), version.getId(),
                LOG4SHELL_CVE, userId, confirmedAt));
        audit.recordSystem(organizationId, "FINDING_CONFIRMED", "finding", finding.getId().toString(),
                Map.of("cveId", LOG4SHELL_CVE, "eventId", event.getId().toString(), "sample", true));
        JsonNode drafts = readDrafts();
        for (ReportType type : ReportType.values()) {
            Report report = reports.save(new Report(event, type, confirmedAt));
            DraftContent content = DraftContent.of(type, drafts.get(type.name()));
            report.draft(content.toJson(json), "TEMPLATE", null, confirmedAt.plus(Duration.ofHours(2)));
            reports.flush();
            audit.recordSystem(organizationId, "REPORT_DRAFTED", "report", report.getId().toString(),
                    details(event, type));
            if (type == ReportType.EARLY_WARNING) {
                report.approve(userId, confirmedAt.plus(Duration.ofHours(18)));
                audit.recordSystem(organizationId, "REPORT_APPROVED", "report", report.getId().toString(),
                        details(event, type));
                report.markSent(userId, confirmedAt.plus(Duration.ofHours(20)), SAMPLE_ENISA_REFERENCE, now);
                Map<String, Object> sent = details(event, type);
                sent.put("sentAt", report.getSentAt().toString());
                sent.put("enisaReference", SAMPLE_ENISA_REFERENCE);
                audit.recordSystem(organizationId, "REPORT_SENT", "report", report.getId().toString(), sent);
            }
        }
        reports.flush();
    }

    // Arma los detalles de auditoría de un reporte del evento de ejemplo.
    private static Map<String, Object> details(ReportableEvent event, ReportType type) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.getId().toString());
        details.put("type", type.name());
        details.put("cveId", event.getCveId());
        details.put("sample", true);
        return details;
    }

    // Lee los borradores de ejemplo del evento Log4Shell que vienen dentro de la aplicación.
    private JsonNode readDrafts() {
        try (InputStream in = DemoAccountInitializer.class.getResourceAsStream(SAMPLE_DRAFTS)) {
            return json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
