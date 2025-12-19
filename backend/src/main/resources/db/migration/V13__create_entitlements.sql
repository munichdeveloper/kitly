-- Create entitlements table for feature flags and limits per tenant
CREATE TABLE entitlements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    feature_key VARCHAR(100) NOT NULL,
    feature_type VARCHAR(50) NOT NULL,
    limit_value BIGINT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entitlement_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT unique_tenant_feature UNIQUE (tenant_id, feature_key),
    CONSTRAINT check_feature_type CHECK (feature_type IN ('BOOLEAN', 'LIMIT', 'QUOTA'))
);

-- Create indexes for performance
CREATE INDEX idx_entitlements_tenant_id ON entitlements(tenant_id);
CREATE INDEX idx_entitlements_feature_key ON entitlements(feature_key);
CREATE INDEX idx_entitlements_enabled ON entitlements(enabled);
