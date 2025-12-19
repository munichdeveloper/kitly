-- Drop existing foreign key constraints
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS fk_user_roles_user;
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS fk_user_roles_role;

-- Add new UUID-based foreign key columns
ALTER TABLE user_roles ADD COLUMN user_uuid UUID;
ALTER TABLE user_roles ADD COLUMN role_uuid UUID;

-- Populate UUID columns from existing relationships
UPDATE user_roles SET user_uuid = users.uuid 
FROM users WHERE user_roles.user_id = users.id;

UPDATE user_roles SET role_uuid = roles.uuid 
FROM roles WHERE user_roles.role_id = roles.id;

-- Drop old columns and rename new ones
ALTER TABLE user_roles DROP COLUMN user_id;
ALTER TABLE user_roles DROP COLUMN role_id;
ALTER TABLE user_roles RENAME COLUMN user_uuid TO user_id;
ALTER TABLE user_roles RENAME COLUMN role_uuid TO role_id;

-- Make UUID primary keys on main tables
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE users DROP COLUMN id;
ALTER TABLE users RENAME COLUMN uuid TO id;
ALTER TABLE users ADD PRIMARY KEY (id);

ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_pkey;
ALTER TABLE roles DROP COLUMN id;
ALTER TABLE roles RENAME COLUMN uuid TO id;
ALTER TABLE roles ADD PRIMARY KEY (id);

-- Recreate foreign key constraints with UUIDs
ALTER TABLE user_roles 
    ADD CONSTRAINT fk_user_roles_user 
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_roles 
    ADD CONSTRAINT fk_user_roles_role 
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE;

-- Recreate primary key on user_roles
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_pkey;
ALTER TABLE user_roles ADD PRIMARY KEY (user_id, role_id);
