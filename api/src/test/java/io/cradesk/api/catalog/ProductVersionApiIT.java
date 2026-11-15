package io.cradesk.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.support.IntegrationTest;

/** Task 5 specification: product versions and protected delete. */
@IntegrationTest
@Transactional
class ProductVersionApiIT {

	@Autowired
	MockMvc mvc;

	@Test
	void createVersionReturns201() throws Exception {
		String productId = createProduct("Smart Lock");

		addVersion(productId, """
				{"version":"1.0.0","releasedAt":"2026-10-01"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.productId").value(productId))
				.andExpect(jsonPath("$.version").value("1.0.0"))
				.andExpect(jsonPath("$.releasedAt").value("2026-10-01"))
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void locationOfCreatedVersionCanBeFetched() throws Exception {
		String productId = createProduct("Smart Lock");
		MvcResult created = addVersion(productId, "{\"version\":\"1.0.0\"}")
				.andExpect(status().isCreated())
				.andReturn();
		String versionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		String location = created.getResponse().getHeader("Location");

		assertThat(location).isEqualTo("/api/products/" + productId + "/versions/" + versionId);
		mvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(versionId))
				.andExpect(jsonPath("$.productId").value(productId))
				.andExpect(jsonPath("$.version").value("1.0.0"));
	}

	@Test
	void versionUnderWrongProductReturns404() throws Exception {
		String lock = createProduct("Smart Lock");
		String camera = createProduct("Camera");
		String versionId = JsonPath.read(addVersion(lock, "{\"version\":\"1.0.0\"}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/products/{id}/versions/{versionId}", camera, versionId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
		mvc.perform(get("/api/products/{id}/versions/{versionId}", lock, UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void versionIsTrimmed() throws Exception {
		String productId = createProduct("Smart Lock");

		addVersion(productId, """
				{"version":" 1.2.0 "}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value("1.2.0"));
	}

	@Test
	void duplicateVersionSameProductReturns409() throws Exception {
		String productId = createProduct("Smart Lock");
		addVersion(productId, "{\"version\":\"1.0.0\"}").andExpect(status().isCreated());

		addVersion(productId, "{\"version\":\" 1.0.0\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void sameVersionOnOtherProductIsAllowed() throws Exception {
		String lock = createProduct("Smart Lock");
		String camera = createProduct("Camera");
		addVersion(lock, "{\"version\":\"1.0.0\"}").andExpect(status().isCreated());

		addVersion(camera, "{\"version\":\"1.0.0\"}").andExpect(status().isCreated());
	}

	@Test
	void versionForUnknownProductReturns404() throws Exception {
		addVersion(UUID.randomUUID().toString(), "{\"version\":\"1.0.0\"}")
				.andExpect(status().isNotFound());

		mvc.perform(get("/api/products/{id}/versions", UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	void blankVersionReturns400() throws Exception {
		String productId = createProduct("Smart Lock");

		addVersion(productId, "{\"version\":\"  \"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.version").exists());
	}

	@Test
	void listReturnsNewestFirst() throws Exception {
		String productId = createProduct("Smart Lock");
		addVersion(productId, "{\"version\":\"1.0.0\"}").andExpect(status().isCreated());
		addVersion(productId, "{\"version\":\"1.1.0\"}").andExpect(status().isCreated());
		addVersion(productId, "{\"version\":\"2.0.0\"}").andExpect(status().isCreated());

		mvc.perform(get("/api/products/{id}/versions", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].version").value("2.0.0"))
				.andExpect(jsonPath("$[2].version").value("1.0.0"));
	}

	@Test
	void deleteProductWithVersionsReturns409() throws Exception {
		String productId = createProduct("Smart Lock");
		addVersion(productId, "{\"version\":\"1.0.0\"}").andExpect(status().isCreated());

		mvc.perform(delete("/api/products/{id}", productId))
				.andExpect(status().isConflict());

		mvc.perform(get("/api/products/{id}", productId))
				.andExpect(status().isOk());
	}

	private ResultActions addVersion(String productId, String json) throws Exception {
		return mvc.perform(post("/api/products/{id}/versions", productId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
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
