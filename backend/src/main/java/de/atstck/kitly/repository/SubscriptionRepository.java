package de.atstck.kitly.repository;

import org.springframework.data.jpa.repository.Query;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Subscription.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    
    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
    
    @Query("""
            select s from Subscription s join fetch s.plan
            where s.tenant.id = :tenantId and s.status in (ACTIVE, TRIALING)
            order by s.status, s.startsAt desc, s.id
            """)
    List<Subscription> findAccessSubscriptions(UUID tenantId);

    List<Subscription> findByTenantId(UUID tenantId);
    
    List<Subscription> findByStatus(SubscriptionStatus status);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<Subscription> findFirstByStripeSubscriptionId(String stripeSubscriptionId);
}
