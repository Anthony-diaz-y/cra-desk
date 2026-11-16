package io.cradesk.api.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    // Devuelve una página de entradas de la empresa en el orden que pida el Pageable.
    Page<AuditEntry> findByOrganizationId(UUID organizationId, Pageable pageable);

    // Devuelve un tramo de entradas de la empresa con seq mayor al dado, para recorrer la cadena por partes.
    Slice<AuditEntry> findByOrganizationIdAndSeqGreaterThanOrderBySeqAsc(UUID organizationId, long seq,
            Pageable pageable);
}
