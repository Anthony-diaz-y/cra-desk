package io.cradesk.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.support.IntegrationTest;

/**
 * Task 3 specification. Each test runs inside a transaction that is rolled
 * back at the end, so tests don't see each other's data.
 */
@IntegrationTest
@Transactional
class ProductRepositoryIT {

	@Autowired
	ProductRepository products;

	@Test
	void savesAndFindsProduct() {
		Product saved = products.saveAndFlush(
				new Product("Smart Lock", "Wi-Fi door lock", ProductClass.IMPORTANT_CLASS_I));

		Product found = products.findById(saved.getId()).orElseThrow();

		assertThat(found.getId()).isNotNull();
		assertThat(found.getName()).isEqualTo("Smart Lock");
		assertThat(found.getDescription()).isEqualTo("Wi-Fi door lock");
		assertThat(found.getProductClass()).isEqualTo(ProductClass.IMPORTANT_CLASS_I);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	void existsByNameIgnoresCase() {
		products.saveAndFlush(new Product("Smart Lock", null, ProductClass.DEFAULT));

		assertThat(products.existsByNameIgnoreCase("smart lock")).isTrue();
		assertThat(products.existsByNameIgnoreCase("Thermostat")).isFalse();
	}

}
