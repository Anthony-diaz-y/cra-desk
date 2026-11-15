package io.cradesk.api.waitlist;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

    // Indica si ya hay una solicitud con ese email ya normalizado.
    boolean existsByEmail(String normalizedEmail);

    // Lista todas las solicitudes de la más reciente a la más antigua.
    List<AccessRequest> findAllByOrderByCreatedAtDesc();
}
