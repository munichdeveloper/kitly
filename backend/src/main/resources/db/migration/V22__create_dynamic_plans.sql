-- Create entitlement_definitions table
CREATE TABLE entitlement_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255),
    description VARCHAR(1000),
    default_value VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_type_name UNIQUE (type, name)
);

-- Create plans table
CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_plan_code UNIQUE (code)
);

-- Create plan_entitlements junction table
CREATE TABLE plan_entitlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    entitlement_definition_id UUID NOT NULL REFERENCES entitlement_definitions(id) ON DELETE RESTRICT,
    value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_plan_entitlement UNIQUE (plan_id, entitlement_definition_id)
);

-- Create indexes for better query performance
CREATE INDEX idx_entitlement_definitions_type ON entitlement_definitions(type);
CREATE INDEX idx_plans_code ON plans(code);
CREATE INDEX idx_plans_is_active ON plans(is_active);
CREATE INDEX idx_plan_entitlements_plan_id ON plan_entitlements(plan_id);
CREATE INDEX idx_plan_entitlements_entitlement_definition_id ON plan_entitlements(entitlement_definition_id);

-- Seed default entitlement definitions
INSERT INTO entitlement_definitions (type, name, display_name, description, default_value) VALUES
-- Features
('FEATURE', 'ai_assistant', 'AI Assistant', 'Access to AI-powered assistant features', 'false'),

-- App Access
('APP_ACCESS', 'nim', 'NIM Application', 'Access to the NIM application', 'false'),

-- Limits
('LIMIT', 'projects', 'Max Projects', 'Maximum number of projects allowed', '10'),
('LIMIT', 'api_calls_per_month', 'API Calls per Month', 'Maximum API calls per month', '1000');

-- Seed default plans
INSERT INTO plans (code, name, description, is_active, display_order) VALUES
('starter', 'Starter', 'Basic plan for getting started', true, 1),
('business', 'Business', 'Advanced plan for growing teams', true, 2),
('enterprise', 'Enterprise', 'Full-featured plan for large organizations', true, 3);

-- Seed plan entitlements for Starter plan
INSERT INTO plan_entitlements (plan_id, entitlement_definition_id, value)
SELECT
    p.id,
    ed.id,
    CASE
        WHEN ed.type = 'FEATURE' AND ed.name = 'ai_assistant' THEN 'false'
        WHEN ed.type = 'LIMIT' AND ed.name = 'projects' THEN '10'
        WHEN ed.type = 'LIMIT' AND ed.name = 'api_calls_per_month' THEN '1000'
    END
FROM plans p
CROSS JOIN entitlement_definitions ed
WHERE p.code = 'starter'
  AND (
      (ed.type = 'FEATURE' AND ed.name = 'ai_assistant')
      OR (ed.type = 'LIMIT' AND ed.name = 'projects')
      OR (ed.type = 'LIMIT' AND ed.name = 'api_calls_per_month')
  );

-- Seed plan entitlements for Business plan
INSERT INTO plan_entitlements (plan_id, entitlement_definition_id, value)
SELECT
    p.id,
    ed.id,
    CASE
        WHEN ed.type = 'FEATURE' AND ed.name = 'ai_assistant' THEN 'true'
        WHEN ed.type = 'APP_ACCESS' AND ed.name = 'nim' THEN 'true'
        WHEN ed.type = 'LIMIT' AND ed.name = 'projects' THEN '100'
        WHEN ed.type = 'LIMIT' AND ed.name = 'api_calls_per_month' THEN '10000'
    END
FROM plans p
CROSS JOIN entitlement_definitions ed
WHERE p.code = 'business'
  AND (
      (ed.type = 'FEATURE' AND ed.name = 'ai_assistant')
      OR (ed.type = 'APP_ACCESS' AND ed.name = 'nim')
      OR (ed.type = 'LIMIT' AND ed.name = 'projects')
      OR (ed.type = 'LIMIT' AND ed.name = 'api_calls_per_month')
  );

-- Seed plan entitlements for Enterprise plan
INSERT INTO plan_entitlements (plan_id, entitlement_definition_id, value)
SELECT
    p.id,
    ed.id,
    CASE
        WHEN ed.type = 'FEATURE' AND ed.name = 'ai_assistant' THEN 'true'
        WHEN ed.type = 'APP_ACCESS' AND ed.name = 'nim' THEN 'true'
        WHEN ed.type = 'LIMIT' AND ed.name = 'projects' THEN 'unlimited'
        WHEN ed.type = 'LIMIT' AND ed.name = 'api_calls_per_month' THEN 'unlimited'
    END
FROM plans p
CROSS JOIN entitlement_definitions ed
WHERE p.code = 'enterprise'
  AND (
      (ed.type = 'FEATURE' AND ed.name = 'ai_assistant')
      OR (ed.type = 'APP_ACCESS' AND ed.name = 'nim')
      OR (ed.type = 'LIMIT' AND ed.name = 'projects')
      OR (ed.type = 'LIMIT' AND ed.name = 'api_calls_per_month')
  );

