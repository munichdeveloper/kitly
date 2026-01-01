package de.atstck.kitly.entity;

import de.atstck.kitly.entitlement.EntitlementType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Definition of a single entitlement that can be assigned to plans.
 * Each entitlement has a type (FEATURE, APP_ACCESS, LIMIT) and a name.
 */
@Entity
@Table(name = "entitlement_definitions", uniqueConstraints = {
    @UniqueConstraint(name = "unique_type_name", columnNames = {"type", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntitlementDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private EntitlementType type;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 255)
    @Column(name = "display_name")
    private String displayName;

    @Size(max = 1000)
    @Column(name = "description")
    private String description;

    /**
     * The default value for this entitlement if not specified in a plan.
     * Can be "true", "false", "100", "unlimited", etc.
     */
    @Column(name = "default_value")
    private String defaultValue;

    /**
     * Additional metadata for the entitlement definition
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
     * Build the full entitlement key (e.g., "features.ai_assistant")
     */
    public String getFullKey() {
        return type.buildKey(name);
    }
}

