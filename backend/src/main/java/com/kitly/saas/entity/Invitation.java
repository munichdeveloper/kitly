package com.kitly.saas.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;
    
    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false)
    private String email;
    
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false)
    private String role;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;
    
    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, unique = true, name = "token_hash")
    private String tokenHash;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;
    
    @Column(name = "invited_at", nullable = false, updatable = false)
    private LocalDateTime invitedAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    
    @PrePersist
    protected void onCreate() {
        invitedAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(7); // Default 7 days expiry
        }
    }
    
    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        EXPIRED,
        CANCELLED
    }
}
