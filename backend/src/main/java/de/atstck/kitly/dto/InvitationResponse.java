package de.atstck.kitly.dto;

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
    private String invitedByUsername;
    private LocalDateTime invitedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
}
