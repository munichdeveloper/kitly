-- Create entitlement_versions table for cache invalidation tracking
CREATE TABLE entitlement_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entitlement_version_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT unique_tenant_version UNIQUE (tenant_id)
);

-- Create index for performance
CREATE INDEX idx_entitlement_versions_tenant_id ON entitlement_versions(tenant_id);
CREATE INDEX idx_entitlement_versions_updated_at ON entitlement_versions(updated_at);
