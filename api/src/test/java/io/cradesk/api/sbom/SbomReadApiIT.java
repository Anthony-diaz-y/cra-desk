package io.cradesk.api.sbom;

import static io.cradesk.api.sbom.SbomUploadApiIT.createProduct;
import static io.cradesk.api.sbom.SbomUploadApiIT.createVersion;
import static io.cradesk.api.sbom.SbomUploadApiIT.fixture;
import static io.cradesk.api.sbom.SbomUploadApiIT.sbomUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
class SbomReadApiIT {

    @Autowired
    TestTenants tenants;

    @Autowired
    JdbcTemplate jdbc;

    MockMvc acme;
    String productId;
    String versionId;
    String url;

    // Crea una empresa con el producto "Smart Lock" versión 1.0.0.
    @BeforeEach
    void createVersion() throws Exception {
        acme = tenants.mvcFor(tenants.create("Acme"));
        productId = createProduct(acme, "Smart Lock");
        versionId = SbomUploadApiIT.createVersion(acme, productId, "1.0.0");
        url = sbomUrl(productId, versionId);
    }

    // Borra empresas, claves y todo lo que depende de ellas después de cada test.
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE api_keys, organizations, products, product_versions CASCADE");
    }

    // Comprueba que el resumen del SBOM subido se puede leer.
    @Test
    void summaryOfUploadedSbom() throws Exception {
        uploadSyft();

        acme.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(versionId))
                .andExpect(jsonPath("$.componentCount").value(5));
    }

    // Comprueba que una versión sin SBOM responde 404 con un mensaje claro.
    @Test
    void versionWithoutSbomReturns404() throws Exception {
        acme.perform(get(url))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No SBOM uploaded for this version"));
    }

    // Comprueba que los componentes salen paginados y ordenados por nombre.
    @Test
    void componentsArePagedAndSorted() throws Exception {
        uploadSyft();

        acme.perform(get(url + "/components").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("busybox"))
                .andExpect(jsonPath("$.items[0].purl").value("pkg:apk/alpine/busybox@1.36.1-r2"))
                .andExpect(jsonPath("$.items[1].name").value("ca-certificates-bundle"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // Comprueba que un tamaño de página fuera de 1 a 200 responde 400 con error en el campo.
    @Test
    void pageSizeOutOfRangeReturns400() throws Exception {
        uploadSyft();

        acme.perform(get(url + "/components").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
        acme.perform(get(url + "/components").param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
    }

    // Comprueba que la descarga devuelve el documento idéntico al subido con un nombre de archivo limpio.
    @Test
    void documentDownloadIsByteIdentical() throws Exception {
        byte[] original = uploadSyft();

        byte[] downloaded = acme.perform(get(url + "/document"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.cyclonedx+json"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"smart-lock-1.0.0.cdx.json\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(original);
    }

    // Comprueba que otra empresa no puede leer ninguna parte del SBOM.
    @Test
    void otherTenantsSbomIsNotFound() throws Exception {
        uploadSyft();
        MockMvc globex = tenants.mvcFor(tenants.create("Globex"));

        globex.perform(get(url)).andExpect(status().isNotFound());
        globex.perform(get(url + "/components")).andExpect(status().isNotFound());
        globex.perform(get(url + "/document")).andExpect(status().isNotFound());
    }

    // Sube el SBOM de Syft a la versión del test y devuelve sus bytes.
    private byte[] uploadSyft() throws Exception {
        byte[] file = fixture("syft-alpine-1.5.json");
        acme.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(file))
                .andExpect(status().isCreated());
        return file;
    }
}
