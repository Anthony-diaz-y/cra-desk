package io.cradesk.api.identity;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {

    private final ApiKeyService keys;

    // Recibe el servicio de claves de API por inyección de dependencias.
    public ApiKeyController(ApiKeyService keys) {
        this.keys = keys;
    }

    // Crea una clave de API y responde 201 con la clave completa, que no se vuelve a mostrar.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyCreatedResponse create(@Valid @RequestBody CreateApiKeyRequest request) {
        return keys.create(request);
    }

    // Lista las claves de la empresa sin sus secretos.
    @GetMapping
    public List<ApiKeyResponse> list() {
        return keys.list();
    }

    // Revoca una clave y responde 204.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        keys.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
