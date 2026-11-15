package io.cradesk.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.common.Constraints;

@Service
public class ProductService {

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase());

    private final ProductRepository products;
    private final ProductVersionRepository versions;

    // Recibe los repositorios de productos y versiones por inyección de dependencias.
    public ProductService(ProductRepository products, ProductVersionRepository versions) {
        this.products = products;
        this.versions = versions;
    }

    // Crea un producto con el nombre recortado y rechaza nombres repetidos.
    @Transactional
    public ProductResponse create(ProductRequest request) {
        String name = request.name().trim();
        if (products.existsByNameIgnoreCase(name)) {
            throw new DuplicateProductException(name);
        }
        Product product = new Product(name, request.description(), request.productClass());
        return toResponse(saveOrConflict(product, name));
    }

    // Lista los productos ordenados por nombre sin distinguir mayúsculas.
    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAll(BY_NAME).stream().map(ProductService::toResponse).toList();
    }

    // Devuelve un producto o lanza error si no existe.
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(find(id));
    }

    // Modifica un producto y rechaza el nombre si ya lo usa otro producto.
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = find(id);
        String name = request.name().trim();
        if (!product.getName().equalsIgnoreCase(name) && products.existsByNameIgnoreCase(name)) {
            throw new DuplicateProductException(name);
        }
        product.update(name, request.description(), request.productClass());
        return toResponse(saveOrConflict(product, name));
    }

    // Borra un producto solo si no tiene versiones registradas.
    @Transactional
    public void delete(UUID id) {
        Product product = find(id);
        if (versions.existsByProductId(id)) {
            throw new ProductHasVersionsException(id);
        }
        products.delete(product);
    }

    // Agrega una versión al producto y rechaza versiones repetidas.
    @Transactional
    public VersionResponse addVersion(UUID productId, VersionRequest request) {
        Product product = find(productId);
        String version = request.version().trim();
        if (versions.existsByProductIdAndVersion(productId, version)) {
            throw new DuplicateVersionException(version);
        }
        try {
            ProductVersion saved = versions.saveAndFlush(new ProductVersion(product, version, request.releasedAt()));
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)) {
                throw new DuplicateVersionException(version);
            }
            throw e;
        }
    }

    // Lista las versiones de un producto existente.
    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(UUID productId) {
        find(productId);
        return versions.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .map(ProductService::toResponse).toList();
    }

    // Devuelve una versión solo si pertenece al producto indicado.
    @Transactional(readOnly = true)
    public VersionResponse getVersion(UUID productId, UUID versionId) {
        find(productId);
        return versions.findByIdAndProductId(versionId, productId)
                .map(ProductService::toResponse)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
    }

    // Busca un producto por id o lanza error si no existe.
    Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Guarda el producto y convierte un nombre repetido detectado por la base de datos en error 409.
    private Product saveOrConflict(Product product, String name) {
        try {
            return products.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            if (Constraints.isViolated(e, Product.NAME_UNIQUE)) {
                throw new DuplicateProductException(name);
            }
            throw e;
        }
    }

    // Convierte un producto en su respuesta JSON.
    static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getProductClass(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    // Convierte una versión en su respuesta JSON.
    static VersionResponse toResponse(ProductVersion v) {
        return new VersionResponse(v.getId(), v.getProduct().getId(), v.getVersion(), v.getReleasedAt(),
                v.getCreatedAt());
    }
}
