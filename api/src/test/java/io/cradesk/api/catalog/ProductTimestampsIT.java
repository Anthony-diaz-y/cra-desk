package io.cradesk.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import io.cradesk.api.support.IntegrationTest;

@IntegrationTest
class ProductTimestampsIT {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	// Vacía las tablas del catálogo después de cada test.
	@AfterEach
	void cleanUp() {
		jdbc.execute("TRUNCATE product_versions, products");
	}

	// Comprueba que las fechas del POST coinciden con las guardadas en la base de datos.
	@Test
	void timestampsInCreateResponseMatchWhatIsStored() throws Exception {
		String created = mvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Smart Lock","productClass":"DEFAULT"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");

		String fetched = mvc.perform(get("/api/products/{id}", id))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat((String) JsonPath.read(fetched, "$.createdAt"))
				.isEqualTo(JsonPath.read(created, "$.createdAt"));
		assertThat((String) JsonPath.read(fetched, "$.updatedAt"))
				.isEqualTo(JsonPath.read(created, "$.updatedAt"));
	}

}
