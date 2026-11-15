package io.cradesk.api.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    // Busca una clave por su prefijo visible.
    Optional<ApiKey> findByPrefix(String prefix);

    // Lista las claves de una empresa de la más reciente a la más antigua.
    List<ApiKey> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    // Busca una clave solo si pertenece a esa empresa.
    Optional<ApiKey> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
