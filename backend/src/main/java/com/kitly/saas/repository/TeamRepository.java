package com.kitly.saas.repository;

import com.kitly.saas.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    
    List<Team> findByTenantId(UUID tenantId);
    
    Optional<Team> findByTenantIdAndName(UUID tenantId, String name);
    
    Boolean existsByTenantIdAndName(UUID tenantId, String name);
}
