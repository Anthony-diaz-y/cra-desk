package io.cradesk.api.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    // Busca un usuario por su email ya normalizado.
    Optional<UserAccount> findByEmail(String normalizedEmail);

    // Indica si ya existe un usuario con ese email ya normalizado.
    boolean existsByEmail(String normalizedEmail);
}
