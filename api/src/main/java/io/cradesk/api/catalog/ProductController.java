package io.cradesk.api.catalog;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    // Recibe el servicio de productos por inyección de dependencias.
    public ProductController(ProductService service) {
        this.service = service;
    }

    // Crea un producto y responde 201 con la cabecera Location.
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    // Lista los productos ordenados por nombre.
    @GetMapping
    public List<ProductResponse> list() {
        return service.list();
    }

    // Devuelve un producto por su id.
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    // Modifica un producto existente.
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    // Registra una versión del producto y responde 201 con la cabecera Location.
    @PostMapping("/{id}/versions")
    public ResponseEntity<VersionResponse> addVersion(@PathVariable UUID id,
            @Valid @RequestBody VersionRequest request) {
        VersionResponse created = service.addVersion(id, request);
        return ResponseEntity.created(URI.create("/api/products/" + id + "/versions/" + created.id()))
                .body(created);
    }

    // Lista las versiones del producto de la más reciente a la más antigua.
    @GetMapping("/{id}/versions")
    public List<VersionResponse> listVersions(@PathVariable UUID id) {
        return service.listVersions(id);
    }

    // Devuelve una versión concreta del producto.
    @GetMapping("/{id}/versions/{versionId}")
    public VersionResponse getVersion(@PathVariable UUID id, @PathVariable UUID versionId) {
        return service.getVersion(id, versionId);
    }

    // Borra un producto y responde 204.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
