-- Create application_settings table
CREATE TABLE application_settings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT,
    setting_type VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    updated_by UUID,
    CONSTRAINT fk_application_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_application_settings_user FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_tenant_setting_key UNIQUE (tenant_id, setting_key)
);

-- Create index for faster lookups
CREATE INDEX idx_application_settings_tenant_id ON application_settings(tenant_id);
CREATE INDEX idx_application_settings_key ON application_settings(setting_key);
CREATE INDEX idx_application_settings_tenant_public ON application_settings(tenant_id, is_public);

