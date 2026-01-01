package de.atstck.kitly.repository;

import de.atstck.kitly.entity.PlanEntitlement;
import de.atstck.kitly.entity.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, UUID> {

    /**
     * Find all entitlements for a specific plan
     */
    List<PlanEntitlement> findByPlan(PlanEntity plan);

    /**
     * Delete all entitlements for a specific plan
     */
    void deleteByPlan(PlanEntity plan);
}

