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
public class CurrentSessionResponse {
    
    private UUID userId;
    private String username;
    private UUID tenantId;
    private String tenantName;
    private List<String> roles;
    private Long entitlementVersion;
}
