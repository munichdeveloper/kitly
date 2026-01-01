package de.atstck.kitly.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dynamic plan definition that can be configured at runtime.
 * Replaces the static PlanCatalog with database-backed plan definitions.
 */
@Entity
@Table(name = "plans", uniqueConstraints = {
    @UniqueConstraint(name = "unique_plan_code", columnNames = {"code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "entitlements")
@EqualsAndHashCode(exclude = "entitlements")
public class PlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 1000)
    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * Entitlements associated with this plan
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlanEntitlement> entitlements = new ArrayList<>();

    /**
     * Additional metadata for the plan
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

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

    /**
     * Helper method to add an entitlement to this plan
     */
    public void addEntitlement(PlanEntitlement entitlement) {
        entitlements.add(entitlement);
        entitlement.setPlan(this);
    }

    /**
     * Helper method to remove an entitlement from this plan
     */
    public void removeEntitlement(PlanEntitlement entitlement) {
        entitlements.remove(entitlement);
        entitlement.setPlan(null);
    }
}

