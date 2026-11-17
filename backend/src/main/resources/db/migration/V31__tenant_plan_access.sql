CREATE TABLE tenant_plan_access (
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_code VARCHAR(50) NOT NULL REFERENCES plans(code) ON UPDATE CASCADE ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (tenant_id, plan_code)
);
