package de.atstck.kitly.repository;

import de.atstck.kitly.entity.Entitlement;
import de.atstck.kitly.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {
    
    List<Entitlement> findByTenant(Tenant tenant);
    
    List<Entitlement> findByTenantAndEnabled(Tenant tenant, Boolean enabled);
    
    Optional<Entitlement> findByTenantAndFeatureKey(Tenant tenant, String featureKey);
    
    List<Entitlement> findByFeatureKey(String featureKey);
}
