-- Enable UUID extension for PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Add UUID columns to existing tables (will be populated in next migration)
ALTER TABLE users ADD COLUMN uuid UUID DEFAULT uuid_generate_v4();
ALTER TABLE roles ADD COLUMN uuid UUID DEFAULT uuid_generate_v4();

-- Generate UUIDs for existing records (if any)
UPDATE users SET uuid = uuid_generate_v4() WHERE uuid IS NULL;
UPDATE roles SET uuid = uuid_generate_v4() WHERE uuid IS NULL;
