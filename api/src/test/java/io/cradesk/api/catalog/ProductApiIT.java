package io.cradesk.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.support.IntegrationTest;

/**
 * Task 4 specification: the products REST API.
 *
 * <p>Every test runs inside a transaction that is rolled back at the end, so
 * the database is always empty at the start of each test.
 */
@IntegrationTest
@Transactional
class ProductApiIT {

	@Autowired
	MockMvc mvc;

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

	@Test
	void getUnknownIdReturns404() throws Exception {
		mvc.perform(get("/api/products/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

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

	@Test
	void deleteReturns204ThenGetReturns404() throws Exception {
		String id = createProduct("Gateway");

		mvc.perform(delete("/api/products/{id}", id))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/products/{id}", id))
				.andExpect(status().isNotFound());
	}

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

	@Test
	void updateUnknownIdReturns404() throws Exception {
		mvc.perform(put("/api/products/{id}", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isNotFound());
	}

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

	@Test
	void deleteUnknownIdReturns404() throws Exception {
		mvc.perform(delete("/api/products/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

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

	@Test
	void unknownProductClassReturns400ProblemDetail() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"SUPER_CRITICAL"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void malformedJsonReturns400ProblemDetail() throws Exception {
		mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": "))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void invalidUuidInPathReturns400ProblemDetail() throws Exception {
		mvc.perform(get("/api/products/{id}", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

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
