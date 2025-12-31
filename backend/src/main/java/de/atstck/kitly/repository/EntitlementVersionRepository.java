package de.atstck.kitly.repository;

import de.atstck.kitly.entity.EntitlementVersion;
import de.atstck.kitly.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitlementVersionRepository extends JpaRepository<EntitlementVersion, UUID> {
    
    Optional<EntitlementVersion> findByTenant(Tenant tenant);
}
