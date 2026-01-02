package de.atstck.kitly.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Junction table between plans and entitlement definitions.
 * Defines the value of a specific entitlement for a plan.
 */
@Entity
@Table(name = "plan_entitlements", uniqueConstraints = {
    @UniqueConstraint(name = "unique_plan_entitlement", columnNames = {"plan_id", "entitlement_definition_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    @JsonBackReference
    private PlanEntity plan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entitlement_definition_id", nullable = false)
    private EntitlementDefinition entitlementDefinition;

    /**
     * The value for this entitlement in this plan.
     * Can be "true", "false", "100", "unlimited", etc.
     */
    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

