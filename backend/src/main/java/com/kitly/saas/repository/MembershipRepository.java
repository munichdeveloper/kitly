package com.kitly.saas.repository;

import com.kitly.saas.entity.Membership;
import com.kitly.saas.entity.Membership.MembershipRole;
import com.kitly.saas.entity.Membership.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    
    List<Membership> findByTenantId(UUID tenantId);
    
    List<Membership> findByUserId(UUID userId);
    
    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    
    List<Membership> findByTenantIdAndStatus(UUID tenantId, MembershipStatus status);
    
    List<Membership> findByTenantIdAndRole(UUID tenantId, MembershipRole role);
    
    long countByTenantIdAndRole(UUID tenantId, MembershipRole role);
    
    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);
}
