package io.cradesk.api.catalog;

import java.util.UUID;

public class VersionNotFoundException extends RuntimeException {
    public VersionNotFoundException(UUID versionId) {
        super("Version " + versionId + " not found for this product");
    }
}
