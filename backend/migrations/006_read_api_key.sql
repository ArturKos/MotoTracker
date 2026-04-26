-- Adds a read-only API key per user, used to embed the dashboard in
-- external systems (Home Assistant iframe, etc.) without password login.
-- Endpoints that mutate state must reject this token; auth.php exposes
-- $auth_readonly so they can.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS read_api_key VARCHAR(64) DEFAULT NULL;

-- Backfill any pre-existing rows with a random 32-hex token.
UPDATE users
SET read_api_key = SUBSTRING(SHA2(CONCAT(UUID(), RAND(), id), 256), 1, 32)
WHERE read_api_key IS NULL;

ALTER TABLE users
    MODIFY COLUMN read_api_key VARCHAR(64) NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_read_api_key ON users(read_api_key);
