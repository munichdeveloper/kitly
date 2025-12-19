package com.kitly.saas.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantContext {
    
    private UUID tenantId;
    private String tenantSlug;
    private UUID userId;
    
    public static TenantContext of(UUID tenantId) {
        return TenantContext.builder()
                .tenantId(tenantId)
                .build();
    }
    
    public static TenantContext of(UUID tenantId, UUID userId) {
        return TenantContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .build();
    }
}
