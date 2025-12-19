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
public class CreateInviteResponse {
    
    private UUID id;
    private UUID tenantId;
    private String email;
    private String role;
    private String token; // Only returned on creation
    private LocalDateTime expiresAt;
}
