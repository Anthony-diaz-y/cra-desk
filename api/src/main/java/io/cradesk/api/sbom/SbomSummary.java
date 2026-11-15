package io.cradesk.api.sbom;

import java.time.Instant;
import java.util.UUID;

public record SbomSummary(UUID id, UUID productId, UUID versionId, String specVersion, String serialNumber,
        Integer bomVersion, int componentCount, int componentsWithoutPurl, String sha256, int sizeBytes,
        UploadChannel uploadedVia, Instant uploadedAt) {

    // Arma el resumen de un SBOM guardado para el producto indicado.
    static SbomSummary of(Sbom sbom, UUID productId) {
        return new SbomSummary(sbom.getId(), productId, sbom.getProductVersionId(), sbom.getSpecVersion(),
                sbom.getSerialNumber(), sbom.getBomVersion(), sbom.getComponentCount(),
                sbom.getComponentsWithoutPurl(), sbom.getSha256(), sbom.getSizeBytes(), sbom.getUploadedVia(),
                sbom.getUploadedAt());
    }
}
