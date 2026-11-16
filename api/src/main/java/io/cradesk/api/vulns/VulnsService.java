package io.cradesk.api.vulns;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.catalog.ProductService;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.identity.CurrentTenant;
import io.cradesk.api.sbom.SbomNotFoundException;
import io.cradesk.api.sbom.SbomRepository;

@Service
public class VulnsService {

    static final Comparator<VulnerabilityResponse> KEV_FIRST = Comparator
            .comparing(VulnerabilityResponse::kev).reversed()
            .thenComparing(VulnerabilityResponse::id)
            .thenComparing(VulnerabilityResponse::componentName);

    private final ProductService products;
    private final ProductVersionRepository versions;
    private final SbomRepository sboms;
    private final VersionScanRepository scans;
    private final VulnerabilityMatchRepository matches;
    private final FindingRepository findings;
    private final KevEntryRepository kevEntries;
    private final ScanQueue queue;
    private final CurrentTenant tenant;

    // Recibe el catálogo, los repositorios, la cola de escaneos y el acceso a la empresa actual.
    public VulnsService(ProductService products, ProductVersionRepository versions, SbomRepository sboms,
            VersionScanRepository scans, VulnerabilityMatchRepository matches, FindingRepository findings,
            KevEntryRepository kevEntries, ScanQueue queue, CurrentTenant tenant) {
        this.products = products;
        this.versions = versions;
        this.sboms = sboms;
        this.scans = scans;
        this.matches = matches;
        this.findings = findings;
        this.kevEntries = kevEntries;
        this.queue = queue;
        this.tenant = tenant;
    }

    // Devuelve el estado del escaneo y las vulnerabilidades de una versión de la empresa actual, KEV primero.
    @Transactional(readOnly = true)
    public VersionVulnerabilities forVersion(UUID productId, UUID versionId) {
        ProductVersion version = versionWithSbom(productId, versionId);
        VersionScan scan = scans.findById(version.getId()).orElse(null);
        List<VulnerabilityMatch> found = matches.findByProductVersionId(version.getId());
        Map<String, LocalDate> kevDates = kevDates(found.stream()
                .filter(VulnerabilityMatch::isKev)
                .flatMap(m -> m.getVulnerability().getAliases().stream())
                .toList());
        List<VulnerabilityResponse> items = found.stream().map(m -> {
            Vulnerability v = m.getVulnerability();
            LocalDate kevDate = m.isKev() ? firstKevDate(v, kevDates) : null;
            return new VulnerabilityResponse(v.getId(), v.getAliases(), v.getSummary(), v.getSeverity(), m.isKev(),
                    kevDate, m.getComponent().getName(), m.getComponent().getVersion(), m.getComponent().getPurl());
        }).sorted(KEV_FIRST).toList();
        return scan == null
                ? new VersionVulnerabilities(null, null, null, null, null, items)
                : new VersionVulnerabilities(scan.getStatus(), scan.getFinishedAt(), scan.getError(),
                        scan.getComponentsScanned(), scan.getComponentsNotScanned(), items);
    }

    // Pide escanear una versión de la empresa actual que tenga SBOM.
    public void requestScan(UUID productId, UUID versionId) {
        ProductVersion version = versionWithSbom(productId, versionId);
        queue.request(version.getId());
    }

    // Lista los hallazgos de la empresa actual con ese estado, del más reciente al más antiguo.
    @Transactional(readOnly = true)
    public List<FindingResponse> findings(FindingStatus status) {
        List<Finding> pending = findings.findByOrganizationIdAndStatusOrderByCreatedAtDesc(tenant.organizationId(),
                status);
        Map<String, LocalDate> kevDates = kevDates(pending.stream().map(Finding::getCveId).toList());
        return pending.stream().map(f -> {
            ProductVersion version = versions.findById(f.getProductVersionId()).orElseThrow();
            Vulnerability v = f.getVulnerability();
            return new FindingResponse(f.getId(), version.getProduct().getId(), version.getProduct().getName(),
                    version.getId(), version.getVersion(), v.getId(), f.getCveId(), v.getSummary(), v.getSeverity(),
                    kevDates.get(f.getCveId()), f.isPresent(), f.getStatus(), f.getCreatedAt());
        }).toList();
    }

    // Busca la versión dentro de la empresa actual y exige que tenga SBOM, si no lanza 404.
    private ProductVersion versionWithSbom(UUID productId, UUID versionId) {
        ProductVersion version = products.findVersion(productId, versionId);
        if (sboms.findByProductVersionId(version.getId()).isEmpty()) {
            throw new SbomNotFoundException();
        }
        return version;
    }

    // Devuelve la fecha de alta en KEV de cada CVE de la lista que esté en el catálogo.
    private Map<String, LocalDate> kevDates(List<String> ids) {
        List<String> cves = ids.stream().map(id -> id.toUpperCase(Locale.ROOT)).filter(id -> id.startsWith("CVE-"))
                .distinct().toList();
        if (cves.isEmpty()) {
            return Map.of();
        }
        return kevEntries.findAllByCveIdIn(cves).stream()
                .collect(Collectors.toMap(KevEntry::getCveId, KevEntry::getDateAdded));
    }

    // Devuelve la fecha KEV del primer identificador de la vulnerabilidad que esté en el catálogo.
    private static LocalDate firstKevDate(Vulnerability v, Map<String, LocalDate> kevDates) {
        return Stream.concat(Stream.of(v.getId()), v.getAliases().stream())
                .map(id -> kevDates.get(id.toUpperCase(Locale.ROOT)))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }
}
