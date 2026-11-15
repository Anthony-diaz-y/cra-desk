package io.cradesk.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cradesk.api.common.Constraints;
import io.cradesk.api.support.IntegrationTest;

/**
 * Real database constraints, without the test-wide rollback transaction: every
 * statement here really commits, so PostgreSQL raises the errors a concurrent
 * request would hit. The tables are emptied after each test.
 */
@IntegrationTest
class CatalogConstraintsIT {

	@Autowired
	ProductRepository products;

	@Autowired
	ProductVersionRepository versions;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.execute("TRUNCATE product_versions, products");
	}

	@Test
	void duplicateProductNameViolatesNamedUniqueIndex() {
		products.saveAndFlush(new Product("Smart Lock", null, ProductClass.DEFAULT));

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> products.saveAndFlush(new Product("SMART LOCK", null, ProductClass.DEFAULT)));

		assertThat(Constraints.isViolated(e, Product.NAME_UNIQUE)).isTrue();
		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isFalse();
	}

	@Test
	void duplicateVersionViolatesNamedUniqueConstraint() {
		Product lock = products.saveAndFlush(new Product("Smart Lock", null, ProductClass.DEFAULT));
		versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null));

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null)));

		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isTrue();
	}

	@Test
	void versionOfDeletedProductIsNotReportedAsDuplicate() {
		// Simulates a version being added while another request deletes the product.
		Product lock = products.saveAndFlush(new Product("Smart Lock", null, ProductClass.DEFAULT));
		jdbc.update("DELETE FROM products WHERE id = ?", lock.getId());

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null)));

		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isFalse();
		assertThat(Constraints.isViolated(e, Product.NAME_UNIQUE)).isFalse();
	}

}
