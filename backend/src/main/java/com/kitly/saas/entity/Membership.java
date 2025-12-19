package com.kitly.saas.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "memberships", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "user_id"})
})
@EntityListeners(com.kitly.saas.entitlement.listener.MembershipEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MembershipRole role = MembershipRole.MEMBER;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;
    
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum MembershipRole {
        OWNER,
        ADMIN,
        MEMBER
    }
    
    public enum MembershipStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED
    }
}
