package de.atstck.kitly.repository;

import de.atstck.kitly.entity.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<PlanEntity, UUID> {

    /**
     * Find a plan by its code
     */
    Optional<PlanEntity> findByCode(String code);

    /**
     * Find a plan by its code (case-insensitive)
     */
    Optional<PlanEntity> findByCodeIgnoreCase(String code);

    /**
     * Find all active plans
     */
    List<PlanEntity> findByIsActiveTrue();

    /**
     * Find all active plans ordered by display order
     */
    List<PlanEntity> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * Find all plans (including inactive) ordered by display order
     */
    List<PlanEntity> findAllByOrderByDisplayOrderAsc();

    /**
     * Find all plans with their entitlements eagerly loaded (case-insensitive)
     */
    @Query("SELECT DISTINCT p FROM PlanEntity p LEFT JOIN FETCH p.entitlements WHERE LOWER(p.code) = LOWER(:code)")
    Optional<PlanEntity> findByCodeWithEntitlements(String code);
}

