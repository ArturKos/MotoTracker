-- Multi-user migration. Adds a users table and an owning user_id on devices.
-- Does NOT seed any admin account; run tools/bootstrap_admin.php afterwards
-- so the existing $gps_db_write_api_key from gps_track_config.php becomes
-- the admin's write_api_key (keeps the HA push + firmware ingest working).

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) DEFAULT NULL,
    write_api_key VARCHAR(64) NOT NULL UNIQUE,
    is_admin TINYINT(1) NOT NULL DEFAULT 0,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add user_id to devices (nullable initially so existing rows survive the ALTER).
ALTER TABLE devices ADD COLUMN IF NOT EXISTS user_id INT DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);

-- FK will be added by bootstrap_admin.php once every device row has a user_id.
