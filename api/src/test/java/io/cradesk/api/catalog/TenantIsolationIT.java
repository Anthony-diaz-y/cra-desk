package io.cradesk.api.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
@Transactional
class TenantIsolationIT {

	@Autowired
	TestTenants tenants;

	MockMvc acme;
	MockMvc globex;
	String globexProductId;
	String globexVersionId;

	// Crea dos empresas y un producto con una versión que pertenece solo a Globex.
	@BeforeEach
	void createTenants() throws Exception {
		acme = tenants.mvcFor(tenants.create("Acme"));
		globex = tenants.mvcFor(tenants.create("Globex"));
		globexProductId = JsonPath.read(globex.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
		globexVersionId = JsonPath.read(globex.perform(post("/api/products/{id}/versions", globexProductId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":\"1.0.0\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
	}

	// Comprueba que una empresa no puede leer, modificar ni borrar el producto de otra.
	@Test
	void otherTenantsProductIsNotFound() throws Exception {
		acme.perform(get("/api/products/{id}", globexProductId))
				.andExpect(status().isNotFound());
		acme.perform(put("/api/products/{id}", globexProductId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Hijacked","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isNotFound());
		acme.perform(delete("/api/products/{id}", globexProductId))
				.andExpect(status().isNotFound());

		globex.perform(get("/api/products/{id}", globexProductId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Smart Lock"));
	}

	// Comprueba que una empresa no puede ver ni agregar versiones en el producto de otra.
	@Test
	void otherTenantsVersionsAreNotFound() throws Exception {
		acme.perform(get("/api/products/{id}/versions", globexProductId))
				.andExpect(status().isNotFound());
		acme.perform(post("/api/products/{id}/versions", globexProductId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":\"9.9.9\"}"))
				.andExpect(status().isNotFound());
		acme.perform(get("/api/products/{id}/versions/{versionId}", globexProductId, globexVersionId))
				.andExpect(status().isNotFound());
	}

	// Comprueba que la lista de productos solo muestra los de la propia empresa.
	@Test
	void listShowsOnlyOwnProducts() throws Exception {
		acme.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		globex.perform(get("/api/products"))
				.andExpect(jsonPath("$.length()").value(1));
	}

	// Comprueba que dos empresas pueden tener un producto con el mismo nombre.
	@Test
	void sameNameAllowedInDifferentTenants() throws Exception {
		acme.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isCreated());
	}

	// Comprueba que dentro de la misma empresa el nombre repetido sigue dando 409.
	@Test
	void duplicateNameInSameTenantStillConflicts() throws Exception {
		globex.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"smart lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isConflict());
	}
}
