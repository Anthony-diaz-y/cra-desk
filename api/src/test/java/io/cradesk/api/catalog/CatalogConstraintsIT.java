package io.cradesk.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import io.cradesk.api.common.Constraints;
import io.cradesk.api.identity.AccessRole;
import io.cradesk.api.identity.Organization;
import io.cradesk.api.identity.OrganizationRepository;
import io.cradesk.api.identity.TenantPrincipal;
import io.cradesk.api.support.IntegrationTest;

@IntegrationTest
class CatalogConstraintsIT {

	@Autowired
	ProductRepository products;

	@Autowired
	OrganizationRepository organizations;

	Organization org;

	// Crea la empresa dueña de los productos de cada test.
	@BeforeEach
	void createOrganization() {
		org = organizations.saveAndFlush(new Organization("Acme"));
	}

	@Autowired
	ProductVersionRepository versions;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ProductService service;

	@Autowired
	TransactionTemplate transactions;

	// Vacía las tablas del catálogo después de cada test.
	@AfterEach
	void cleanUp() {
		jdbc.execute("TRUNCATE organizations, products, product_versions CASCADE");
	}

	// Comprueba que un nombre repetido viola el índice único de productos.
	@Test
	void duplicateProductNameViolatesNamedUniqueIndex() {
		products.saveAndFlush(new Product(org.getId(), "Smart Lock", null, ProductClass.DEFAULT));

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> products.saveAndFlush(new Product(org.getId(), "SMART LOCK", null, ProductClass.DEFAULT)));

		assertThat(Constraints.isViolated(e, Product.NAME_UNIQUE)).isTrue();
		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isFalse();
	}

	// Comprueba que una versión repetida viola la restricción única de versiones.
	@Test
	void duplicateVersionViolatesNamedUniqueConstraint() {
		Product lock = products.saveAndFlush(new Product(org.getId(), "Smart Lock", null, ProductClass.DEFAULT));
		versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null));

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null)));

		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isTrue();
	}

	// Comprueba que un error de clave foránea no se toma como duplicado.
	@Test
	void versionOfDeletedProductIsNotReportedAsDuplicate() {
		Product lock = products.saveAndFlush(new Product(org.getId(), "Smart Lock", null, ProductClass.DEFAULT));
		jdbc.update("DELETE FROM products WHERE id = ?", lock.getId());

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null)));

		assertThat(Constraints.isViolated(e, ProductVersion.VERSION_UNIQUE)).isFalse();
		assertThat(Constraints.isViolated(e, Product.NAME_UNIQUE)).isFalse();
	}


	// Comprueba que borrar un producto mientras otra petición le agrega una versión responde 409 y no 500.
	@Test
	void deleteWaitsForConcurrentVersionInsert() throws Exception {
		Product lock = products.saveAndFlush(new Product(org.getId(), "Smart Lock", null, ProductClass.DEFAULT));
		CountDownLatch versionInserted = new CountDownLatch(1);
		AtomicReference<Throwable> deleteError = new AtomicReference<>();

		Thread adder = new Thread(() -> {
			signIn();
			transactions.executeWithoutResult(status -> {
				service.addVersion(lock.getId(), new VersionRequest("1.0.0", null));
				versionInserted.countDown();
				sleep(500);
			});
		});
		Thread deleter = new Thread(() -> {
			signIn();
			await(versionInserted);
			try {
				service.delete(lock.getId());
			} catch (Throwable e) {
				deleteError.set(e);
			}
		});
		adder.start();
		deleter.start();
		adder.join(10_000);
		deleter.join(10_000);

		assertThat(deleteError.get()).isInstanceOf(ProductHasVersionsException.class);
		assertThat(products.findById(lock.getId())).isPresent();
	}

	// Autentica el hilo actual como miembro de la empresa del test.
	private void signIn() {
		TenantPrincipal principal = new TenantPrincipal(org.getId(), null, "acme@example.com", AccessRole.MEMBER);
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
	}

	// Espera a que el contador llegue a cero, como máximo cinco segundos.
	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// Duerme el hilo actual esa cantidad de milisegundos.
	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
