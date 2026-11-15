package io.cradesk.api.vulns;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionScanRepository extends JpaRepository<VersionScan, UUID> {

    // Busca el escaneo con ese estado que se pidió primero.
    Optional<VersionScan> findFirstByStatusOrderByRequestedAtAsc(ScanStatus status);
}
