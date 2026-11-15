package io.cradesk.api.vulns;

import java.util.List;

public interface KevFeedClient {

    // Descarga el catálogo completo de vulnerabilidades explotadas de CISA.
    List<KevRecord> download();
}
