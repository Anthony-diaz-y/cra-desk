package io.cradesk.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.common.Constraints;

/**
 * Business rules for products. Controllers call this; it talks to the
 * repository. Names are trimmed and must be unique ignoring case.
 */
@Service
public class ProductService {

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase());

    private final ProductRepository products;
    private final ProductVersionRepository versions;

    public ProductService(ProductRepository products, ProductVersionRepository versions) {
        this.products = products;
        this.versions = versions;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String name = request.name().trim();
        if (products.existsByNameIgnoreCase(name)) {
            throw new DuplicateProductException(name);
        }
        Product product = new Product(name, request.description(), request.productClass());
        return toResponse(saveOrConflict(product, name));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAll(BY_NAME).stream().map(ProductService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(find(id));
    }

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

    @Transactional
    public void delete(UUID id) {
        Product product = find(id);
        if (versions.existsByProductId(id)) {
            throw new ProductHasVersionsException(id);
        }
        products.delete(product);
    }

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

    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(UUID productId) {
        find(productId);
        return versions.findByProductIdOrderByCreatedAtDescIdDesc(productId).stream()
                .map(ProductService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VersionResponse getVersion(UUID productId, UUID versionId) {
        find(productId);
        return versions.findByIdAndProductId(versionId, productId)
                .map(ProductService::toResponse)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
    }

    Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Flushes immediately so that (1) @PreUpdate runs before we build the
     * response and (2) a duplicate that slipped past the exists() check
     * (two requests at the same instant) hits the unique index here and
     * becomes a 409 instead of a 500. Any other integrity error is rethrown:
     * it is not a duplicate and must not be reported as one.
     */
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

    static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getProductClass(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    static VersionResponse toResponse(ProductVersion v) {
        return new VersionResponse(v.getId(), v.getProduct().getId(), v.getVersion(), v.getReleasedAt(),
                v.getCreatedAt());
    }
}
