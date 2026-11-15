package io.cradesk.api.vulns;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.cradesk.api.sbom.SbomRepository;

@Component
public class ScanScheduler {

    static final int MAX_SCANS_PER_PASS = 20;

    private static final Logger log = LoggerFactory.getLogger(ScanScheduler.class);

    private final ScanProperties properties;
    private final VersionScanner scanner;
    private final ScanQueue queue;
    private final KevCatalogService kev;
    private final SbomRepository sboms;

    // Recibe la configuración, el escáner, la cola, el catálogo KEV y el repositorio de SBOM.
    public ScanScheduler(ScanProperties properties, VersionScanner scanner, ScanQueue queue, KevCatalogService kev,
            SbomRepository sboms) {
        this.properties = properties;
        this.scanner = scanner;
        this.queue = queue;
        this.kev = kev;
        this.sboms = sboms;
    }

    // Cada pocos segundos procesa la cola de escaneos, si el planificador está activado.
    @Scheduled(fixedDelayString = "${cradesk.scan.poll-interval:PT30S}")
    public void processQueue() {
        if (properties.schedulerEnabled()) {
            drain();
        }
    }

    // Cada día actualiza KEV y vuelve a escanear todo, si el planificador está activado.
    @Scheduled(cron = "${cradesk.scan.daily-cron:0 0 3 * * *}", zone = "UTC")
    public void daily() {
        if (properties.schedulerEnabled()) {
            runDaily();
        }
    }

    // Al arrancar descarga el catálogo KEV si nunca se descargó, sin impedir el arranque si falla.
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (properties.schedulerEnabled() && kev.isEmpty()) {
            refreshKev();
        }
    }

    // Escanea pendientes hasta vaciar la cola o hasta 20 por pasada, y devuelve cuántos escaneó.
    public int drain() {
        int scanned = 0;
        while (scanned < MAX_SCANS_PER_PASS && scanner.scanNextPending()) {
            scanned++;
        }
        return scanned;
    }

    // Actualiza el catálogo KEV (si falla, sigue) y encola el escaneo de todas las versiones con SBOM.
    public void runDaily() {
        refreshKev();
        for (UUID versionId : sboms.findAllProductVersionIds()) {
            queue.request(versionId);
        }
    }

    // Descarga el catálogo KEV registrando en el log si falla.
    private void refreshKev() {
        try {
            log.info("KEV catalog refreshed: {} entries", kev.refresh());
        } catch (ExternalServiceException e) {
            log.warn("KEV catalog refresh failed, keeping the previous catalog: {}", e.getMessage());
        }
    }
}
