package io.cradesk.api.vulns;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VulnsController {

    private final VulnsService vulns;

    // Recibe el servicio de vulnerabilidades por inyección de dependencias.
    public VulnsController(VulnsService vulns) {
        this.vulns = vulns;
    }

    // Devuelve el estado del escaneo y las vulnerabilidades de la versión.
    @GetMapping("/api/products/{productId}/versions/{versionId}/vulnerabilities")
    public VersionVulnerabilities vulnerabilities(@PathVariable UUID productId, @PathVariable UUID versionId) {
        return vulns.forVersion(productId, versionId);
    }

    // Pide escanear la versión y responde 202 porque el escaneo corre en segundo plano.
    @PostMapping("/api/products/{productId}/versions/{versionId}/scan")
    public ResponseEntity<Void> scan(@PathVariable UUID productId, @PathVariable UUID versionId) {
        vulns.requestScan(productId, versionId);
        return ResponseEntity.accepted().build();
    }

    // Lista los hallazgos pendientes de revisión de la empresa.
    @GetMapping("/api/findings")
    public List<FindingResponse> findings() {
        return vulns.findings();
    }
}
