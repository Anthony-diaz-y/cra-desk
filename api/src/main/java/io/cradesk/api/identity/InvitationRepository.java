package io.cradesk.api.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    // Busca una invitación por el hash de su código.
    Optional<Invitation> findByCodeHash(String codeHash);
}
