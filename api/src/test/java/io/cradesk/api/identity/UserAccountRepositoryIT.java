package io.cradesk.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.common.Constraints;
import io.cradesk.api.support.IntegrationTest;

@IntegrationTest
@Transactional
class UserAccountRepositoryIT {

	@Autowired
	OrganizationRepository organizations;

	@Autowired
	UserAccountRepository users;

	Organization acme;

	// Crea la empresa que usan los tests.
	@BeforeEach
	void createOrganization() {
		acme = organizations.saveAndFlush(new Organization("Acme"));
	}

	// Comprueba que el email se guarda recortado y en minúsculas.
	@Test
	void savesUserWithNormalizedEmail() {
		UserAccount saved = users.saveAndFlush(new UserAccount(acme, " Alice@Example.com ", "x", AccessRole.MEMBER));

		UserAccount found = users.findByEmail("alice@example.com").orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getEmail()).isEqualTo("alice@example.com");
		assertThat(found.getOrganization().getId()).isEqualTo(acme.getId());
		assertThat(found.getRole()).isEqualTo(AccessRole.MEMBER);
		assertThat(users.existsByEmail("alice@example.com")).isTrue();
	}

	// Comprueba que no se pueden repetir emails aunque cambien las mayúsculas.
	@Test
	void duplicateEmailIgnoringCaseIsRejected() {
		users.saveAndFlush(new UserAccount(acme, "alice@example.com", "x", AccessRole.MEMBER));

		DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
				() -> users.saveAndFlush(new UserAccount(acme, "ALICE@example.com", "x", AccessRole.MEMBER)));

		assertThat(Constraints.isViolated(e, UserAccount.EMAIL_UNIQUE)).isTrue();
	}

	// Comprueba que el quinto fallo de login bloquea la cuenta durante 15 minutos.
	@Test
	void fifthFailedLoginLocksForFifteenMinutes() {
		UserAccount alice = new UserAccount(acme, "alice@example.com", "x", AccessRole.MEMBER);
		Instant now = Instant.parse("2026-11-15T10:00:00Z");

		for (int i = 0; i < 4; i++) {
			alice.recordFailedLogin(now);
		}
		assertThat(alice.isLockedAt(now)).isFalse();

		alice.recordFailedLogin(now);
		assertThat(alice.isLockedAt(now.plus(Duration.ofMinutes(14)))).isTrue();
		assertThat(alice.isLockedAt(now.plus(Duration.ofMinutes(16)))).isFalse();
	}

	// Comprueba que un login correcto reinicia el contador de fallos.
	@Test
	void successfulLoginResetsCounter() {
		UserAccount alice = new UserAccount(acme, "alice@example.com", "x", AccessRole.MEMBER);
		Instant now = Instant.parse("2026-11-15T10:00:00Z");

		for (int i = 0; i < 4; i++) {
			alice.recordFailedLogin(now);
		}
		alice.recordSuccessfulLogin();
		alice.recordFailedLogin(now);

		assertThat(alice.getFailedLoginAttempts()).isEqualTo(1);
		assertThat(alice.isLockedAt(now)).isFalse();
	}
}
