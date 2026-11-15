package io.cradesk.api.sbom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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

    private final CycloneDxParser parser;
    private final ProductService products;
    private final SbomRepository sboms;
    private final SbomComponentRepository components;
    private final CurrentTenant tenant;
    private final TransactionTemplate transactions;
    private final SbomProperties properties;

    // Recibe el parser, el catálogo, los repositorios, el usuario actual, las transacciones y la configuración.
    public SbomService(CycloneDxParser parser, ProductService products, SbomRepository sboms,
            SbomComponentRepository components, CurrentTenant tenant, TransactionTemplate transactions,
            SbomProperties properties) {
        this.parser = parser;
        this.products = products;
        this.sboms = sboms;
        this.components = components;
        this.tenant = tenant;
        this.transactions = transactions;
        this.properties = properties;
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

    // Reemplaza el SBOM de la versión por uno nuevo con sus componentes; debe llamarse dentro de una transacción.
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
        return new UploadResult(SbomSummary.of(sbom, productId), replaced);
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
