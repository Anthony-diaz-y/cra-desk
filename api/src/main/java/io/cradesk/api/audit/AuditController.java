package io.cradesk.api.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.cradesk.api.audit.AuditService.AuditPage;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;

    // Recibe el servicio de auditoría por inyección de dependencias.
    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    // Devuelve una página del registro de auditoría de la empresa, lo más reciente primero.
    @GetMapping
    public AuditPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return audit.page(page, size);
    }

    // Verifica que la cadena de hashes de la empresa esté completa y sin alteraciones.
    @GetMapping("/verify")
    public AuditVerification verify() {
        return audit.verify();
    }
}
