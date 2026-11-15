package io.cradesk.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
@Transactional
class ProductApiIT {

	@Autowired
	TestTenants tenants;

	MockMvc mvc;

	// Inicia sesión como miembro de una empresa nueva antes de cada test.
	@BeforeEach
	void signIn() {
		mvc = tenants.mvcFor(tenants.create("Acme"));
	}

	// Comprueba que crear un producto responde 201 con Location.
	@Test
	void createReturns201WithLocation() throws Exception {
		MvcResult result = mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","description":"Wi-Fi door lock","productClass":"IMPORTANT_CLASS_I"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.name").value("Smart Lock"))
				.andExpect(jsonPath("$.description").value("Wi-Fi door lock"))
				.andExpect(jsonPath("$.productClass").value("IMPORTANT_CLASS_I"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists())
				.andReturn();

		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

		assertThat(result.getResponse().getHeader("Location"))
				.isEqualTo("/api/products/" + id);
	}

	// Comprueba que la lista sale ordenada por nombre.
	@Test
	void listIsSortedByName() throws Exception {
		createProduct("Zeta Sensor");
		createProduct("alpha Meter");
		createProduct("Beta Camera");

		mvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].name").value("alpha Meter"))
				.andExpect(jsonPath("$[1].name").value("Beta Camera"))
				.andExpect(jsonPath("$[2].name").value("Zeta Sensor"));
	}

	// Comprueba que un id inexistente responde 404.
	@Test
	void getUnknownIdReturns404() throws Exception {
		mvc.perform(get("/api/products/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	// Comprueba que un nombre en blanco responde 400 con error en el campo.
	@Test
	void blankNameReturns400WithFieldError() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"   ","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists());
	}

	// Comprueba que un nombre de más de 120 caracteres responde 400.
	@Test
	void nameLongerThan120Returns400() throws Exception {
		String longName = "N".repeat(121);

		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","productClass":"DEFAULT"}
								""".formatted(longName)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists());
	}

	// Comprueba que un nombre repetido con otras mayúsculas responde 409.
	@Test
	void duplicateNameDifferentCaseReturns409() throws Exception {
		createProduct("Smart Lock");

		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"smart lock ","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	// Comprueba que modificar cambia los campos y la fecha de modificación.
	@Test
	void updateChangesFieldsAndUpdatedAt() throws Exception {
		MvcResult created = mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Thermostat","description":"Old","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		String body = created.getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		Instant createdAt = Instant.parse(JsonPath.read(body, "$.createdAt"));

		MvcResult updated = mvc.perform(put("/api/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Thermostat","description":"New","productClass":"CRITICAL"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value("Smart Thermostat"))
				.andExpect(jsonPath("$.description").value("New"))
				.andExpect(jsonPath("$.productClass").value("CRITICAL"))
				.andReturn();

		String updatedBody = updated.getResponse().getContentAsString();
		Instant updatedCreatedAt = Instant.parse(JsonPath.read(updatedBody, "$.createdAt"));
		Instant updatedUpdatedAt = Instant.parse(JsonPath.read(updatedBody, "$.updatedAt"));

		assertThat(updatedCreatedAt).isEqualTo(createdAt);
		assertThat(updatedUpdatedAt).isAfter(createdAt);
	}

	// Comprueba que un producto borrado ya no se encuentra.
	@Test
	void deleteReturns204ThenGetReturns404() throws Exception {
		String id = createProduct("Gateway");

		mvc.perform(delete("/api/products/{id}", id))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/products/{id}", id))
				.andExpect(status().isNotFound());
	}

	// Comprueba que renombrar con el nombre de otro producto responde 409.
	@Test
	void updateToAnotherProductsNameReturns409() throws Exception {
		createProduct("Smart Lock");
		String camera = createProduct("Camera");

		mvc.perform(put("/api/products/{id}", camera)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"SMART LOCK","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isConflict());
	}

	// Comprueba que cambiar solo las mayúsculas del propio nombre está permitido.
	@Test
	void updateChangingOnlyCaseOfOwnNameReturns200() throws Exception {
		String id = createProduct("smart lock");

		mvc.perform(put("/api/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Smart Lock"));
	}

	// Comprueba que modificar un id inexistente responde 404.
	@Test
	void updateUnknownIdReturns404() throws Exception {
		mvc.perform(put("/api/products/{id}", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isNotFound());
	}

	// Comprueba que modificar con nombre vacío responde 400.
	@Test
	void updateWithBlankNameReturns400() throws Exception {
		String id = createProduct("Smart Lock");

		mvc.perform(put("/api/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists());
	}

	// Comprueba que borrar un id inexistente responde 404.
	@Test
	void deleteUnknownIdReturns404() throws Exception {
		mvc.perform(delete("/api/products/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	// Comprueba que sin productClass responde 400 con error en el campo.
	@Test
	void missingProductClassReturns400WithFieldError() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.productClass").exists());
	}

	// Comprueba que un productClass desconocido responde 400.
	@Test
	void unknownProductClassReturns400ProblemDetail() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"SUPER_CRITICAL"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.productClass").exists())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// Comprueba que un JSON mal formado responde 400.
	@Test
	void malformedJsonReturns400ProblemDetail() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": "))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// Comprueba que un id que no es UUID responde 400.
	@Test
	void invalidUuidInPathReturns400ProblemDetail() throws Exception {
		mvc.perform(get("/api/products/{id}", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// Comprueba que un PUT con una versión de fila vieja responde 409 en vez de pisar el cambio de otro.
	@Test
	void staleRowVersionReturns409() throws Exception {
		String id = createProduct("Thermostat");

		mvc.perform(put("/api/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Thermostat v2","productClass":"DEFAULT","rowVersion":0}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rowVersion").value(1));

		mvc.perform(put("/api/products/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Thermostat v3","productClass":"DEFAULT","rowVersion":0}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Product was modified by someone else"));
	}

	// Comprueba que los espacios Unicode y repetidos dentro del nombre se reducen a uno solo.
	@Test
	void namesCollapseInnerAndUnicodeSpaces() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\" Smart\u2003  Lock\u00a0\",\"productClass\":\"DEFAULT\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Smart Lock"));

		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"smart lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isConflict());
	}

	// Comprueba que un nombre hecho solo de espacios no separables responde 400.
	@Test
	void nameOfOnlyNonBreakingSpacesReturns400() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\u00a0\u00a0\",\"productClass\":\"DEFAULT\"}"))
				.andExpect(status().isBadRequest());
	}

	// Comprueba que una descripción en blanco se guarda como null.
	@Test
	void blankDescriptionIsStoredAsNull() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Gateway","description":"   ","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").value(nullValue()));
	}

	// Crea un producto de prueba y devuelve su id.
	private String createProduct(String name) throws Exception {
		MvcResult result = mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","productClass":"DEFAULT"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

}
