package io.cradesk.api.vulns;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface VersionScanRepository extends JpaRepository<VersionScan, UUID> {

    // Pasa a pendientes todos los escaneos que quedaron en curso y devuelve cuántos eran.
    @Transactional
    @Modifying
    @Query("update VersionScan s set s.status = io.cradesk.api.vulns.ScanStatus.PENDING"
            + " where s.status = io.cradesk.api.vulns.ScanStatus.RUNNING")
    int requeueRunning();

    // Busca el escaneo con ese estado que se pidió primero.
    Optional<VersionScan> findFirstByStatusOrderByRequestedAtAsc(ScanStatus status);
}
