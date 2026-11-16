package io.cradesk.api.vulns;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.sbom.SbomComponentRepository;
import io.cradesk.api.sbom.SbomRepository;

@Service
public class VersionScanner {

    static final String INTERNAL_ERROR = "Internal error during scan";

    private static final Logger log = LoggerFactory.getLogger(VersionScanner.class);

    private final VersionScanRepository scans;
    private final SbomRepository sboms;
    private final SbomComponentRepository components;
    private final ProductVersionRepository versions;
    private final VulnerabilityRepository vulnerabilities;
    private final VulnerabilityMatchRepository matches;
    private final FindingRepository findings;
    private final KevCatalogService kev;
    private final OsvClient osv;
    private final TransactionTemplate transactions;
    private final Clock clock;

    // Recibe los repositorios, el catálogo KEV, el cliente de OSV, las transacciones y el reloj.
    public VersionScanner(VersionScanRepository scans, SbomRepository sboms, SbomComponentRepository components,
            ProductVersionRepository versions, VulnerabilityRepository vulnerabilities,
            VulnerabilityMatchRepository matches, FindingRepository findings, KevCatalogService kev, OsvClient osv,
            TransactionTemplate transactions, Clock clock) {
        this.scans = scans;
        this.sboms = sboms;
        this.components = components;
        this.versions = versions;
        this.vulnerabilities = vulnerabilities;
        this.matches = matches;
        this.findings = findings;
        this.kev = kev;
        this.osv = osv;
        this.transactions = transactions;
        this.clock = clock;
    }

    // Escanea el siguiente escaneo pendiente, si hay alguno, y devuelve si escaneó algo.
    public boolean scanNextPending() {
        return scans.findFirstByStatusOrderByRequestedAtAsc(ScanStatus.PENDING)
                .map(scan -> {
                    scan(scan.getProductVersionId());
                    return true;
                })
                .orElse(false);
    }

    // Escanea una versión: lee sus componentes, consulta OSV fuera de transacción y guarda todo de una vez.
    public void scan(UUID versionId) {
        Targets targets = transactions.execute(status -> start(versionId));
        try {
            if (targets.error() != null) {
                throw new ExternalServiceException(targets.error());
            }
            Map<String, List<OsvHit>> hits = osv.queryByPurl(new ArrayList<>(targets.byPurl().keySet()));
            Map<String, OsvVulnerability> fetched = fetchChanged(hits);
            transactions.executeWithoutResult(status -> save(versionId, targets, hits, fetched));
        } catch (RuntimeException e) {
            String reason = e instanceof ExternalServiceException ? e.getMessage() : INTERNAL_ERROR;
            if (e instanceof ExternalServiceException) {
                log.warn("Scan of version {} failed: {}", versionId, e.getMessage());
            } else {
                log.error("Scan of version {} failed", versionId, e);
            }
            transactions.executeWithoutResult(status -> scans.findById(versionId)
                    .ifPresent(scan -> scan.fail(clock.instant(), reason)));
        }
    }

    // Marca el escaneo en curso y devuelve los componentes que se pueden escanear agrupados por purl.
    private Targets start(UUID versionId) {
        VersionScan scan = scans.findById(versionId).orElseGet(() -> new VersionScan(versionId));
        if (scan.getStatus() == null) {
            scan.request(clock.instant());
        }
        scan.start(clock.instant());
        scans.save(scan);
        return sboms.findByProductVersionId(versionId).map(sbom -> {
            Map<String, List<UUID>> byPurl = new LinkedHashMap<>();
            int notScanned = 0;
            for (var component : components.findAllBySbomId(sbom.getId())) {
                String purl = component.getPurl();
                if (hasVersion(purl)) {
                    byPurl.computeIfAbsent(purl, p -> new ArrayList<>()).add(component.getId());
                } else {
                    notScanned++;
                }
            }
            int scanned = byPurl.values().stream().mapToInt(List::size).sum();
            return new Targets(sbom.getId(), byPurl, scanned, notScanned, null);
        }).orElse(new Targets(null, Map.of(), 0, 0, "No SBOM uploaded for this version"));
    }

    // Descarga de OSV el detalle de las vulnerabilidades nuevas o modificadas desde la última vez.
    private Map<String, OsvVulnerability> fetchChanged(Map<String, List<OsvHit>> hits) {
        Map<String, Instant> latest = new HashMap<>();
        hits.values().forEach(list -> list.forEach(hit -> latest.merge(hit.id(), hit.modified(),
                (a, b) -> a.isAfter(b) ? a : b)));
        Map<String, Vulnerability> known = vulnerabilities.findAllById(latest.keySet()).stream()
                .collect(Collectors.toMap(Vulnerability::getId, Function.identity()));
        Map<String, OsvVulnerability> fetched = new HashMap<>();
        for (Map.Entry<String, Instant> entry : latest.entrySet()) {
            Vulnerability current = known.get(entry.getKey());
            if (current == null || !current.getModified().equals(micros(entry.getValue()))) {
                OsvVulnerability detail = osv.fetch(entry.getKey());
                if (detail != null) {
                    fetched.put(entry.getKey(), detail);
                }
            }
        }
        return fetched;
    }

    // Si el SBOM sigue siendo el mismo, guarda vulnerabilidades, coincidencias y hallazgos y termina el escaneo.
    private void save(UUID versionId, Targets targets, Map<String, List<OsvHit>> hits,
            Map<String, OsvVulnerability> fetched) {
        Instant now = clock.instant();
        UUID currentSbom = sboms.findLockedByProductVersionId(versionId).map(s -> s.getId()).orElse(null);
        if (!targets.sbomId().equals(currentSbom)) {
            scans.findById(versionId).ifPresent(scan -> scan.requeueIfRunning(now));
            return;
        }
        for (OsvVulnerability v : fetched.values()) {
            vulnerabilities.findById(v.id()).ifPresentOrElse(
                    existing -> existing.update(v.summary(), v.severity(), v.aliases(), v.published(), v.modified(),
                            now),
                    () -> vulnerabilities.save(new Vulnerability(v.id(), v.summary(), v.severity(), v.aliases(),
                            v.published(), v.modified(), now)));
        }
        vulnerabilities.flush();
        Set<String> ids = new HashSet<>();
        hits.values().forEach(list -> list.forEach(hit -> ids.add(hit.id())));
        Map<String, Vulnerability> byId = vulnerabilities.findAllById(ids).stream()
                .collect(Collectors.toMap(Vulnerability::getId, Function.identity()));
        Map<String, String> kevCveById = kevCves(byId.values());

        matches.deleteByProductVersionId(versionId);
        for (Map.Entry<String, List<OsvHit>> entry : hits.entrySet()) {
            for (UUID componentId : targets.byPurl().getOrDefault(entry.getKey(), List.of())) {
                for (OsvHit hit : entry.getValue()) {
                    Vulnerability vulnerability = byId.get(hit.id());
                    if (vulnerability != null) {
                        matches.save(new VulnerabilityMatch(versionId, components.getReferenceById(componentId),
                                vulnerability, kevCveById.containsKey(hit.id())));
                    }
                }
            }
        }
        updateFindings(versionId, byId, kevCveById, now);
        scans.findById(versionId).ifPresent(scan -> scan.finish(now, targets.scanned(), targets.notScanned()));
    }

    // Crea un hallazgo por cada CVE de KEV nuevo, marca vistos los que siguen y ausentes los que ya no aparecen.
    private void updateFindings(UUID versionId, Map<String, Vulnerability> byId, Map<String, String> kevCveById,
            Instant now) {
        UUID organizationId = versions.findById(versionId).orElseThrow().getProduct().getOrganizationId();
        Map<String, Finding> existing = findings.findByProductVersionId(versionId).stream()
                .collect(Collectors.toMap(Finding::getCveId, Function.identity()));
        Map<String, Vulnerability> recordByCve = new TreeMap<>();
        new TreeMap<>(kevCveById).forEach((vulnerabilityId, cve) ->
                recordByCve.putIfAbsent(cve, byId.get(vulnerabilityId)));
        recordByCve.forEach((cve, vulnerability) -> {
            Finding finding = existing.get(cve);
            if (finding == null) {
                findings.save(new Finding(organizationId, versionId, vulnerability, cve, now));
            } else {
                finding.seenAt(now);
            }
        });
        existing.forEach((cve, finding) -> {
            if (!recordByCve.containsKey(cve)) {
                finding.markAbsent();
            }
        });
    }

    // Indica si el purl trae versión: una @ en el último segmento, antes de los calificadores.
    static boolean hasVersion(String purl) {
        if (purl == null) {
            return false;
        }
        String path = purl.split("[?#]", 2)[0];
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && path.indexOf('@', lastSlash) > lastSlash + 1;
    }

    // Devuelve, para cada vulnerabilidad que está en KEV, el CVE por el que está.
    private Map<String, String> kevCves(Iterable<Vulnerability> found) {
        Map<String, List<String>> candidates = new HashMap<>();
        for (Vulnerability v : found) {
            List<String> ids = new ArrayList<>(v.getAliases());
            ids.add(0, v.getId());
            candidates.put(v.getId(), ids);
        }
        Set<String> inKev = kev.kevCves(candidates.values().stream().flatMap(List::stream).toList());
        Map<String, String> result = new HashMap<>();
        candidates.forEach((id, aliases) -> aliases.stream()
                .map(a -> a.toUpperCase(Locale.ROOT))
                .filter(inKev::contains)
                .findFirst()
                .ifPresent(cve -> result.put(id, cve)));
        return result;
    }

    // Recorta un instante a microsegundos para compararlo con lo guardado.
    private static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    private record Targets(UUID sbomId, Map<String, List<UUID>> byPurl, int scanned, int notScanned, String error) {
    }
}
