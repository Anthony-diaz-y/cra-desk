package io.cradesk.api.vulns;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class KevCatalogService {

    private final KevFeedClient feed;
    private final KevEntryRepository entries;
    private final TransactionTemplate transactions;
    private final Clock clock;

    // Recibe el cliente del feed, el repositorio del catálogo, las transacciones y el reloj.
    public KevCatalogService(KevFeedClient feed, KevEntryRepository entries, TransactionTemplate transactions,
            Clock clock) {
        this.feed = feed;
        this.entries = entries;
        this.transactions = transactions;
        this.clock = clock;
    }

    // Descarga el catálogo fuera de la transacción y lo reemplaza entero; si falla o viene vacío, deja el anterior.
    public int refresh() {
        List<KevRecord> records = feed.download();
        if (records.isEmpty()) {
            throw new ExternalServiceException("KEV: empty catalog");
        }
        Instant now = clock.instant();
        Map<String, KevEntry> byCve = new LinkedHashMap<>();
        for (KevRecord r : records) {
            String cve = r.cveId().toUpperCase(Locale.ROOT);
            byCve.putIfAbsent(cve, new KevEntry(cve, r.vendorProject(), r.product(), r.vulnerabilityName(),
                    r.dateAdded(), r.dueDate(), r.knownRansomware(), r.shortDescription(), now));
        }
        transactions.executeWithoutResult(status -> {
            entries.deleteAllInBatch();
            entries.saveAll(byCve.values());
        });
        return byCve.size();
    }

    // Indica si el catálogo todavía no se descargó nunca.
    public boolean isEmpty() {
        return entries.count() == 0;
    }

    // Devuelve cuáles de esos ids son CVE presentes en el catálogo, en mayúsculas.
    public Set<String> kevCves(Collection<String> ids) {
        Set<String> cves = ids.stream()
                .map(id -> id.toUpperCase(Locale.ROOT))
                .filter(id -> id.startsWith("CVE-"))
                .collect(Collectors.toSet());
        if (cves.isEmpty()) {
            return Set.of();
        }
        return entries.findAllByCveIdIn(cves).stream().map(KevEntry::getCveId).collect(Collectors.toSet());
    }
}
