package io.cradesk.api.waitlist;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccessRequestController {

    private final AccessRequestService requests;

    // Recibe el servicio de solicitudes de acceso por inyección de dependencias.
    public AccessRequestController(AccessRequestService requests) {
        this.requests = requests;
    }

    // Recibe una solicitud pública de acceso y responde 202 siempre que los datos sean válidos.
    @PostMapping("/api/access-requests")
    public ResponseEntity<Void> submit(@Valid @RequestBody AccessRequestForm form) {
        requests.submit(form);
        return ResponseEntity.accepted().build();
    }

    // Lista las solicitudes de acceso para el admin.
    @GetMapping("/api/admin/access-requests")
    public List<AccessRequestResponse> list() {
        return requests.list();
    }
}
