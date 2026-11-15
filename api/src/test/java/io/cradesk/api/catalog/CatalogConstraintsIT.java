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

@IntegrationTest
class CatalogConstraintsIT {

	@Autowired
	ProductRepository products;

	@Autowired
	ProductVersionRepository versions;

	@Autowired
	JdbcTemplate jdbc;

	// Vacía las tablas del catálogo después de cada test.
	@AfterEach
	void cleanUp() {
		jdbc.execute("TRUNCATE product_versions, products");
	}

	// Comprueba que un nombre repetido viola el índice único de productos.
	@Test
	void duplicateProductNameViolatesNamedUniqueIndex() {
		products.saveAndFlush(new Product("Smart Lock", null, ProductClass.DEFAULT));

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> products.saveAndFlush(new Product("SMART LOCK", null, ProductClass.DEFAULT)));

		assertThat(Constraints.isViolated(e, Product.NAME_UNIQUE)).isTrue();
		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isFalse();
	}

	// Comprueba que una versión repetida viola la restricción única de versiones.
	@Test
	void duplicateVersionViolatesNamedUniqueConstraint() {
		Product lock = products.saveAndFlush(new Product("Smart Lock", null, ProductClass.DEFAULT));
		versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null));

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null)));

		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isTrue();
	}

	// Comprueba que un error de clave foránea no se toma como duplicado.
	@Test
	void versionOfDeletedProductIsNotReportedAsDuplicate() {
		Product lock = products.saveAndFlush(new Product("Smart Lock", null, ProductClass.DEFAULT));
		jdbc.update("DELETE FROM products WHERE id = ?", lock.getId());

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null)));

		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isFalse();
		assertThat(Constraints.isViolated(e, Product.NAME_UNIQUE)).isFalse();
	}

}
