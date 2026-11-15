package io.cradesk.api.vulns;

import static io.cradesk.api.support.FakeExternalFeeds.kev;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cradesk.api.support.FakeExternalFeeds.FakeKevFeedClient;
import io.cradesk.api.support.IntegrationTest;

@IntegrationTest
class KevCatalogIT {

    @Autowired
    KevCatalogService catalog;

    @Autowired
    FakeKevFeedClient feed;

    @Autowired
    JdbcTemplate jdbc;

    // Vacía el catálogo y el doble del feed después de cada test.
    @AfterEach
    void cleanUp() {
        feed.reset();
        jdbc.execute("TRUNCATE kev_entries");
    }

    // Comprueba que cada actualización reemplaza el catálogo completo.
    @Test
    void refreshReplacesCatalog() {
        feed.willReturn(kev("CVE-2021-44228"), kev("CVE-2023-4966"));
        assertThat(catalog.refresh()).isEqualTo(2);

        feed.willReturn(kev("CVE-2022-22965"));
        catalog.refresh();

        assertThat(cves()).containsExactly("CVE-2022-22965");
    }

    // Comprueba que si la descarga falla se conserva el catálogo anterior.
    @Test
    void failedDownloadKeepsPreviousCatalog() {
        feed.willReturn(kev("CVE-2021-44228"));
        catalog.refresh();

        feed.willFail();
        ExternalServiceException e = catchThrowableOfType(ExternalServiceException.class, () -> catalog.refresh());

        assertThat(e).isNotNull();
        assertThat(cves()).containsExactly("CVE-2021-44228");
    }

    // Comprueba que un catálogo vacío no borra el anterior.
    @Test
    void emptyFeedKeepsPreviousCatalog() {
        feed.willReturn(kev("CVE-2021-44228"));
        catalog.refresh();

        feed.willReturn();
        catchThrowableOfType(ExternalServiceException.class, () -> catalog.refresh());

        assertThat(cves()).containsExactly("CVE-2021-44228");
        assertThat(catalog.isEmpty()).isFalse();
    }

    // Comprueba que la búsqueda de CVE en KEV no distingue mayúsculas e ignora ids que no son CVE.
    @Test
    void kevLookupIgnoresCase() {
        feed.willReturn(kev("CVE-2021-44228"), kev("CVE-2021-44228"));
        catalog.refresh();

        assertThat(catalog.kevCves(List.of("cve-2021-44228", "GHSA-jfh8-c2jp-5v3q", "CVE-1999-0001")))
                .containsExactly("CVE-2021-44228");
    }

    // Lista los CVE guardados en el catálogo, ordenados.
    private List<String> cves() {
        return jdbc.queryForList("SELECT cve_id FROM kev_entries ORDER BY cve_id", String.class);
    }
}
