package io.cradesk.api.vulns;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.cradesk.api.sbom.SbomUploadedEvent;

@Component
public class ScanQueue {

    private final VersionScanRepository scans;
    private final Clock clock;

    // Recibe el repositorio de escaneos y el reloj.
    public ScanQueue(VersionScanRepository scans, Clock clock) {
        this.scans = scans;
        this.clock = clock;
    }

    // Pide escanear la versión en una transacción propia: si ya está pendiente no cambia nada.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void request(UUID versionId) {
        enqueue(versionId);
    }

    // Después de confirmar la subida de un SBOM, pide escanear su versión en una transacción nueva.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSbomUploaded(SbomUploadedEvent event) {
        enqueue(event.productVersionId());
    }

    // Deja el escaneo pendiente salvo que ya lo esté; si está corriendo, se repetirá al terminar.
    private void enqueue(UUID versionId) {
        VersionScan scan = scans.findById(versionId).orElseGet(() -> new VersionScan(versionId));
        if (scan.getStatus() == ScanStatus.PENDING) {
            return;
        }
        scan.request(clock.instant());
        scans.save(scan);
    }
}
