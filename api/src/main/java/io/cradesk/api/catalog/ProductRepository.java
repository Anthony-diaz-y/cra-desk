package io.cradesk.api.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Busca un producto solo si pertenece a esa empresa.
    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // Busca un producto de esa empresa y bloquea su fila (SELECT ... FOR UPDATE) hasta el final de la transacción.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.organizationId = :organizationId")
    Optional<Product> findLockedByIdAndOrganizationId(@Param("id") UUID id,
            @Param("organizationId") UUID organizationId);

    // Busca un producto de esa empresa por nombre sin distinguir mayúsculas y bloquea su fila.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.organizationId = :organizationId and lower(p.name) = lower(:name)")
    Optional<Product> findLockedByNameInOrganization(@Param("organizationId") UUID organizationId,
            @Param("name") String name);

    // Lista los productos de una empresa con el orden indicado.
    List<Product> findAllByOrganizationId(UUID organizationId, Sort sort);

    // Indica si la empresa ya tiene un producto con ese nombre, sin distinguir mayúsculas.
    @Query("select count(p) > 0 from Product p where p.organizationId = :organizationId and lower(p.name) = lower(:name)")
    boolean existsInOrganization(@Param("organizationId") UUID organizationId, @Param("name") String name);
}
