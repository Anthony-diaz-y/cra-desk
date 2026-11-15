package io.cradesk.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for products. Controllers call this; it talks to the
 * repository. Names are trimmed and must be unique ignoring case.
 */
@Service
public class ProductService {

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase());

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
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
        products.delete(find(id));
    }

    Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Flushes immediately so that (1) @PreUpdate runs before we build the
     * response and (2) a duplicate that slipped past the exists() check
     * (two requests at the same instant) hits the unique index here and
     * becomes a 409 instead of a 500.
     */
    private Product saveOrConflict(Product product, String name) {
        try {
            return products.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateProductException(name);
        }
    }

    static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getProductClass(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
