package io.cradesk.api.vulns;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KevEntryRepository extends JpaRepository<KevEntry, String> {

    // Busca las entradas del catálogo KEV de esos CVE.
    List<KevEntry> findAllByCveIdIn(Collection<String> cveIds);
}
