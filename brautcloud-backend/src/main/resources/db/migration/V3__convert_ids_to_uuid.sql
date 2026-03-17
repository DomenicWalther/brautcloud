-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create new UUID columns for all tables
ALTER TABLE users ADD COLUMN id_uuid UUID DEFAULT uuid_generate_v4();
ALTER TABLE events ADD COLUMN id_uuid UUID DEFAULT uuid_generate_v4();
ALTER TABLE images ADD COLUMN id_uuid UUID DEFAULT uuid_generate_v4();
ALTER TABLE refresh_tokens ADD COLUMN id_uuid UUID DEFAULT uuid_generate_v4();

-- Create new UUID foreign key columns
ALTER TABLE events ADD COLUMN user_id_uuid UUID;
ALTER TABLE images ADD COLUMN event_id_uuid UUID;
ALTER TABLE refresh_tokens ADD COLUMN user_id_uuid UUID;

-- Populate the new UUID foreign key columns by matching the old integer foreign keys
UPDATE events SET user_id_uuid = users.id_uuid FROM users WHERE events.user_id = users.id;
UPDATE images SET event_id_uuid = events.id_uuid FROM events WHERE images.event_id = events.id;
UPDATE refresh_tokens SET user_id_uuid = users.id_uuid FROM users WHERE refresh_tokens.user_id = users.id;

-- Drop old foreign key constraints
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_user_id_fkey;
ALTER TABLE images DROP CONSTRAINT IF EXISTS images_event_id_fkey;
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS fk_refresh_tokens_users;

-- Drop old primary key constraints
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_pkey;
ALTER TABLE images DROP CONSTRAINT IF EXISTS images_pkey;
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_pkey;

-- Drop old columns
ALTER TABLE events DROP COLUMN user_id;
ALTER TABLE images DROP COLUMN event_id;
ALTER TABLE refresh_tokens DROP COLUMN user_id;

ALTER TABLE users DROP COLUMN id;
ALTER TABLE events DROP COLUMN id;
ALTER TABLE images DROP COLUMN id;
ALTER TABLE refresh_tokens DROP COLUMN id;

-- Rename UUID columns to replace old id columns
ALTER TABLE users RENAME COLUMN id_uuid TO id;
ALTER TABLE events RENAME COLUMN id_uuid TO id;
ALTER TABLE images RENAME COLUMN id_uuid TO id;
ALTER TABLE refresh_tokens RENAME COLUMN id_uuid TO id;

ALTER TABLE events RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE images RENAME COLUMN event_id_uuid TO event_id;
ALTER TABLE refresh_tokens RENAME COLUMN user_id_uuid TO user_id;

-- Add new primary key constraints
ALTER TABLE users ADD PRIMARY KEY (id);
ALTER TABLE events ADD PRIMARY KEY (id);
ALTER TABLE images ADD PRIMARY KEY (id);
ALTER TABLE refresh_tokens ADD PRIMARY KEY (id);

-- Add new foreign key constraints
ALTER TABLE events ADD CONSTRAINT events_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE images ADD CONSTRAINT images_event_id_fkey FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;
ALTER TABLE refresh_tokens ADD CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Ensure user_id in refresh_tokens remains unique
ALTER TABLE refresh_tokens ADD CONSTRAINT refresh_tokens_user_id_key UNIQUE (user_id);
