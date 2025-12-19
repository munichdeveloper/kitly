package com.kitly.saas.repository;

import com.kitly.saas.entity.Subscription;
import com.kitly.saas.entity.Subscription.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    
    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
    
    List<Subscription> findByTenantId(UUID tenantId);
    
    List<Subscription> findByStatus(SubscriptionStatus status);
}
