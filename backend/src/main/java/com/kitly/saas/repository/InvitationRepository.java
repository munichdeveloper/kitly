package com.kitly.saas.repository;

import com.kitly.saas.entity.Invitation;
import com.kitly.saas.entity.Invitation.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    
    Optional<Invitation> findByTokenHash(String tokenHash);
    
    List<Invitation> findByTenantId(UUID tenantId);
    
    List<Invitation> findByTenantIdAndStatus(UUID tenantId, InvitationStatus status);
    
    List<Invitation> findByEmailAndStatus(String email, InvitationStatus status);
}
