package io.cradesk.api.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    // Busca un usuario por su email ya normalizado.
    Optional<UserAccount> findByEmail(String normalizedEmail);

    // Busca un usuario por email y bloquea su fila para que los intentos de login simultáneos cuenten uno tras otro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.email = :email")
    Optional<UserAccount> findForLoginByEmail(@Param("email") String normalizedEmail);

    // Indica si ya existe un usuario con ese email ya normalizado.
    boolean existsByEmail(String normalizedEmail);
}
