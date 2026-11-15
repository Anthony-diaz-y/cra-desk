package io.cradesk.api.sbom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.catalog.Product;
import io.cradesk.api.catalog.ProductClass;
import io.cradesk.api.catalog.ProductRepository;
import io.cradesk.api.catalog.ProductVersion;
import io.cradesk.api.catalog.ProductVersionRepository;
import io.cradesk.api.common.Constraints;
import io.cradesk.api.identity.Organization;
import io.cradesk.api.identity.OrganizationRepository;
import io.cradesk.api.support.IntegrationTest;

@IntegrationTest
@Transactional
class SbomRepositoryIT {

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	ProductRepository products;

	@Autowired
	ProductVersionRepository versions;

	@Autowired
	SbomRepository sboms;

	@Autowired
	SbomComponentRepository components;

	@Autowired
	JdbcTemplate jdbc;

	ProductVersion version;

	// Crea una empresa, un producto y una versión a la que se le asigna el SBOM.
	@BeforeEach
	void createVersion() {
		Organization acme = organizations.saveAndFlush(new Organization("Acme"));
		Product lock = products.saveAndFlush(new Product(acme.getId(), "Smart Lock", null, ProductClass.DEFAULT));
		version = versions.saveAndFlush(new ProductVersion(lock, "1.0.0", null));
	}

	// Comprueba que un SBOM se guarda con sus componentes y se recupera paginado y ordenado.
	@Test
	void savesSbomWithComponents() {
		Sbom sbom = sboms.saveAndFlush(newSbom());
		components.saveAllAndFlush(List.of(
				new SbomComponent(sbom, "c", "library", null, "zlib", "1.3", "pkg:apk/alpine/zlib@1.3"),
				new SbomComponent(sbom, "a", "library", null, "busybox", "1.36.1", "pkg:apk/alpine/busybox@1.36.1"),
				new SbomComponent(sbom, "b", "library", "org.example", "musl", "1.2.4", null)));

		Sbom found = sboms.findByProductVersionId(version.getId()).orElseThrow();
		Page<SbomComponent> page = components.findBySbomId(found.getId(), PageRequest.of(0, 10, Sort.by("name")));

		assertThat(found.getSpecVersion()).isEqualTo("1.5");
		assertThat(found.getUploadedVia()).isEqualTo(UploadChannel.WEB);
		assertThat(found.getUploadedAt()).isNotNull();
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getContent()).extracting(SbomComponent::getName).containsExactly("busybox", "musl", "zlib");
	}

	// Comprueba que al borrar un SBOM la base de datos borra también sus componentes.
	@Test
	void deletingSbomDeletesItsComponents() {
		Sbom sbom = sboms.saveAndFlush(newSbom());
		components.saveAndFlush(new SbomComponent(sbom, "a", "library", null, "busybox", "1.36.1", null));

		jdbc.update("DELETE FROM sboms");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM sbom_components", Integer.class)).isZero();
	}

	// Comprueba que una versión no puede tener dos SBOM.
	@Test
	void onlyOneSbomPerVersion() {
		sboms.saveAndFlush(newSbom());

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> sboms.saveAndFlush(newSbom()));

		assertThat(Constraints.isViolated(e, Sbom.VERSION_UNIQUE)).isTrue();
	}

	// Crea un SBOM de prueba para la versión del test.
	private Sbom newSbom() {
		return new Sbom(version.getId(), "1.5", "urn:uuid:3e671687-395b-41f5-a30f-a58921a69b79", 1,
				"a".repeat(64), 120, 3, 1, "{\"bomFormat\":\"CycloneDX\"}", null, UploadChannel.WEB);
	}
}
