package com.kitly.saas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwitchTenantRequest {
    
    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;
}
