package com.kitly.saas.repository;

import com.kitly.saas.entity.EntitlementVersion;
import com.kitly.saas.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitlementVersionRepository extends JpaRepository<EntitlementVersion, UUID> {
    
    Optional<EntitlementVersion> findByTenant(Tenant tenant);
}
