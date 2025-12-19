-- Add idp_subject field to users table for OAuth/SSO integration
ALTER TABLE users ADD COLUMN idp_subject VARCHAR(255);

-- Add unique constraint for idp_subject
ALTER TABLE users ADD CONSTRAINT unique_users_idp_subject UNIQUE (idp_subject);

-- Create index for performance
CREATE INDEX idx_users_idp_subject ON users(idp_subject);
