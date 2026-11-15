package io.cradesk.api.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    // Busca una empresa por su nombre exacto.
    Optional<Organization> findByName(String name);
}
