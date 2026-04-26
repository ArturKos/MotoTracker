-- Admin MVP schema deltas.
-- `must_change_password` already landed in 002_multi_user.sql.
-- This migration adds a soft-disable flag so admins can lock accounts
-- without deleting their history (devices + points stay linked by id).

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active TINYINT(1) NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_users_active ON users(active);
