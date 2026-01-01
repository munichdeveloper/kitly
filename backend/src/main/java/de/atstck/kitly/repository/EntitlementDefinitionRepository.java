package de.atstck.kitly.repository;

import de.atstck.kitly.entity.EntitlementDefinition;
import de.atstck.kitly.entitlement.EntitlementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitlementDefinitionRepository extends JpaRepository<EntitlementDefinition, UUID> {

    /**
     * Find an entitlement definition by type and name
     */
    Optional<EntitlementDefinition> findByTypeAndName(EntitlementType type, String name);

    /**
     * Find all entitlement definitions by type
     */
    List<EntitlementDefinition> findByType(EntitlementType type);

    /**
     * Find all entitlement definitions ordered by type and name
     */
    List<EntitlementDefinition> findAllByOrderByTypeAscNameAsc();
}

