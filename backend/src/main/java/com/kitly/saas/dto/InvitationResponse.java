package com.kitly.saas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationResponse {
    
    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private String email;
    private String role;
    private String status;
    private String token;
    private UUID invitedBy;
    private LocalDateTime invitedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
}
