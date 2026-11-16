package io.cradesk.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.common.Constraints;
import io.cradesk.api.common.Names;
import io.cradesk.api.identity.CurrentTenant;

@Service
public class ProductService {

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase());

    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final CurrentTenant tenant;

    // Recibe los repositorios y el acceso a la empresa de la petición actual.
    public ProductService(ProductRepository products, ProductVersionRepository versions, CurrentTenant tenant) {
        this.products = products;
        this.versions = versions;
        this.tenant = tenant;
    }

    // Crea un producto con el nombre normalizado y rechaza nombres repetidos en la empresa.
    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID organizationId = tenant.organizationId();
        String name = cleanName(request.name());
        if (products.existsInOrganization(organizationId, name)) {
            throw new DuplicateProductException(name);
        }
        Product product = new Product(organizationId, name, Names.blankToNull(request.description()),
                request.productClass());
        return toResponse(saveOrConflict(product, name));
    }

    // Lista los productos de la empresa actual ordenados por nombre sin distinguir mayúsculas.
    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAllByOrganizationId(tenant.organizationId(), BY_NAME).stream()
                .map(ProductService::toResponse).toList();
    }

    // Devuelve un producto o lanza error si no existe.
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(find(id));
    }

    // Modifica un producto, rechaza versiones de fila viejas y nombres que ya usa otro producto.
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = find(id);
        if (request.rowVersion() != null && request.rowVersion() != product.getRowVersion()) {
            throw new StaleProductException();
        }
        String name = cleanName(request.name());
        if (!product.getName().equalsIgnoreCase(name)
                && products.existsInOrganization(product.getOrganizationId(), name)) {
            throw new DuplicateProductException(name);
        }
        product.update(name, Names.blankToNull(request.description()), request.productClass());
        return toResponse(saveOrConflict(product, name));
    }

    // Borra un producto solo si no tiene versiones, bloqueando su fila para no cruzarse con otra petición.
    @Transactional
    public void delete(UUID id) {
        Product product = findForUpdate(id);
        if (versions.existsByProductId(id)) {
            throw new ProductHasVersionsException(id);
        }
        products.delete(product);
    }

    // Agrega una versión al producto bloqueando su fila y rechaza versiones repetidas.
    @Transactional
    public VersionResponse addVersion(UUID productId, VersionRequest request) {
        Product product = findForUpdate(productId);
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

    // Bloquea el producto de la empresa actual y devuelve su versión, o lanza 404 si alguno no existe.
    @Transactional
    public ProductVersion lockVersion(UUID productId, UUID versionId) {
        findForUpdate(productId);
        return versions.findByIdAndProductId(versionId, productId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
    }

    // Busca producto y versión por nombre en la empresa actual y crea la versión si falta y se permite.
    @Transactional
    public ProductVersion resolveVersionByName(String productName, String version, boolean create) {
        String name = Names.normalizeName(productName);
        String text = version.strip();
        Product product = products.findLockedByNameInOrganization(tenant.organizationId(), name)
                .orElseThrow(() -> new ProductNotFoundByNameException(name));
        return versions.findByProductIdAndVersion(product.getId(), text).orElseGet(() -> {
            if (!create) {
                throw new VersionNotFoundByNameException(text, product.getName());
            }
            return versions.saveAndFlush(new ProductVersion(product, text, null));
        });
    }

    // Devuelve la versión de un producto de la empresa actual, o lanza 404 si alguno no existe.
    @Transactional(readOnly = true)
    public ProductVersion findVersion(UUID productId, UUID versionId) {
        find(productId);
        return versions.findByIdAndProductId(versionId, productId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
    }

    // Busca un producto de la empresa actual por id o lanza error si no existe o es de otra empresa.
    Product find(UUID id) {
        return products.findByIdAndOrganizationId(id, tenant.organizationId())
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Busca un producto de la empresa actual y bloquea su fila hasta el final de la transacción.
    private Product findForUpdate(UUID id) {
        return products.findLockedByIdAndOrganizationId(id, tenant.organizationId())
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Normaliza el nombre y rechaza los que quedan vacíos al quitarles los espacios.
    private static String cleanName(String raw) {
        String name = Names.normalizeName(raw);
        if (name.isEmpty()) {
            throw new InvalidProductNameException();
        }
        return name;
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
                p.getCreatedAt(), p.getUpdatedAt(), p.getRowVersion());
    }

    // Convierte una versión en su respuesta JSON.
    static VersionResponse toResponse(ProductVersion v) {
        return new VersionResponse(v.getId(), v.getProduct().getId(), v.getVersion(), v.getReleasedAt(),
                v.getCreatedAt());
    }
}
