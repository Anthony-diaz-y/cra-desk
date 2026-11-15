package io.cradesk.api.sbom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import io.cradesk.api.catalog.ProductService;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.CurrentTenant;
import io.cradesk.api.identity.TenantPrincipal;

@Service
public class SbomService {

    static final MediaType CYCLONEDX_JSON = MediaType.parseMediaType("application/vnd.cyclonedx+json");
    static final List<MediaType> ACCEPTED = List.of(MediaType.APPLICATION_JSON, CYCLONEDX_JSON);
    static final int MAX_PAGE_SIZE = 200;
    static final Sort BY_NAME_AND_VERSION = Sort.by("name", "version");

    private final CycloneDxParser parser;
    private final ProductService products;
    private final SbomRepository sboms;
    private final SbomComponentRepository components;
    private final CurrentTenant tenant;
    private final TransactionTemplate transactions;
    private final SbomProperties properties;
    private final ApplicationEventPublisher events;

    // Recibe el parser, el catálogo, los repositorios, el usuario actual, las transacciones y la configuración.
    public SbomService(CycloneDxParser parser, ProductService products, SbomRepository sboms,
            SbomComponentRepository components, CurrentTenant tenant, TransactionTemplate transactions,
            SbomProperties properties, ApplicationEventPublisher events) {
        this.parser = parser;
        this.products = products;
        this.sboms = sboms;
        this.components = components;
        this.tenant = tenant;
        this.transactions = transactions;
        this.properties = properties;
        this.events = events;
    }

    // Lee y valida el SBOM recibido y lo guarda en la versión, reemplazando el anterior si existía.
    public UploadResult upload(UUID productId, UUID versionId, InputStream body, String contentType)
            throws IOException, HttpMediaTypeNotSupportedException {
        requireCycloneDxJson(contentType);
        byte[] document = readLimited(body);
        ParsedSbom parsed = parser.parse(document);
        TenantPrincipal principal = tenant.principal();
        UploadChannel channel = principal.role() == AccessRole.CLI ? UploadChannel.CLI : UploadChannel.WEB;
        return transactions.execute(status -> {
            ProductVersion version = products.lockVersion(productId, versionId);
            return store(productId, version.getId(), document, parsed, principal.userId(), channel);
        });
    }

    // Reemplaza el SBOM de la versión por uno nuevo y avisa con un evento; debe llamarse dentro de una transacción.
    public UploadResult store(UUID productId, UUID versionId, byte[] document, ParsedSbom parsed, UUID uploadedBy,
            UploadChannel channel) {
        boolean replaced = sboms.findByProductVersionId(versionId).map(previous -> {
            sboms.delete(previous);
            sboms.flush();
            return true;
        }).orElse(false);
        Sbom sbom = sboms.save(new Sbom(versionId, parsed.specVersion(), parsed.serialNumber(), parsed.bomVersion(),
                sha256(document), document.length, parsed.components().size(), parsed.componentsWithoutPurl(),
                new String(document, StandardCharsets.UTF_8), uploadedBy, channel));
        components.saveAll(parsed.components().stream()
                .map(c -> new SbomComponent(sbom, c.bomRef(), c.type(), c.group(), c.name(), c.version(), c.purl()))
                .toList());
        components.flush();
        events.publishEvent(new SbomUploadedEvent(versionId));
        return new UploadResult(SbomSummary.of(sbom, productId), replaced);
    }

    // Devuelve el resumen del SBOM de una versión de la empresa actual, o 404 si no hay.
    @Transactional(readOnly = true)
    public SbomSummary summary(UUID productId, UUID versionId) {
        return SbomSummary.of(find(productId, versionId), productId);
    }

    // Devuelve una página de componentes del SBOM ordenada por nombre y versión.
    @Transactional(readOnly = true)
    public ComponentPage components(UUID productId, UUID versionId, int page, int size) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (page < 0) {
            errors.put("page", "must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.put("size", "must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!errors.isEmpty()) {
            throw new InvalidPageException(errors);
        }
        Sbom sbom = find(productId, versionId);
        Page<SbomComponent> result = components.findBySbomId(sbom.getId(),
                PageRequest.of(page, size, BY_NAME_AND_VERSION));
        return new ComponentPage(result.map(ComponentResponse::of).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    // Devuelve el documento original con un nombre de archivo limpio formado por producto y versión.
    @Transactional(readOnly = true)
    public SbomDocument document(UUID productId, UUID versionId) {
        ProductVersion version = products.findVersion(productId, versionId);
        Sbom sbom = sboms.findByProductVersionId(version.getId()).orElseThrow(SbomNotFoundException::new);
        String filename = safeFileName(version.getProduct().getName() + "-" + version.getVersion()) + ".cdx.json";
        return new SbomDocument(filename, sbom.getDocument().getBytes(StandardCharsets.UTF_8));
    }

    // Busca el SBOM de una versión de la empresa actual o lanza 404.
    private Sbom find(UUID productId, UUID versionId) {
        ProductVersion version = products.findVersion(productId, versionId);
        return sboms.findByProductVersionId(version.getId()).orElseThrow(SbomNotFoundException::new);
    }

    // Deja en el nombre solo letras minúsculas, números, punto, guion y guion bajo.
    static String safeFileName(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        return cleaned.replaceAll("^[-.]+|-+$", "");
    }

    // Rechaza con 415 cualquier tipo de contenido que no sea JSON de CycloneDX.
    private static void requireCycloneDxJson(String contentType) throws HttpMediaTypeNotSupportedException {
        MediaType type;
        try {
            type = contentType == null ? null : MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            type = null;
        }
        MediaType received = type;
        if (received == null || ACCEPTED.stream().noneMatch(accepted -> accepted.equalsTypeAndSubtype(received))) {
            throw new HttpMediaTypeNotSupportedException(received, ACCEPTED);
        }
    }

    // Lee el cuerpo hasta el límite configurado más un byte y lanza 413 si lo supera, sin fiarse de Content-Length.
    private byte[] readLimited(InputStream body) throws IOException {
        int limit = (int) Math.min(properties.maxBytes(), Integer.MAX_VALUE - 1);
        byte[] document = body.readNBytes(limit + 1);
        if (document.length > limit) {
            throw new SbomTooLargeException(properties.maxBytes());
        }
        return document;
    }

    // Calcula el SHA-256 del documento en hexadecimal.
    private static String sha256(byte[] document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record UploadResult(SbomSummary summary, boolean replaced) {
    }
}
