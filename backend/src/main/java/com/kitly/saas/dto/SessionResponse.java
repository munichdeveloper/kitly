package com.kitly.saas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResponse {
    
    private String token;
    private String type = "Bearer";
    private UUID userId;
    private UUID tenantId;
    private List<String> roles;
    private Long entitlementVersion;
    private Long expiresIn; // milliseconds
}
