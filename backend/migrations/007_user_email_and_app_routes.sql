-- 007: add email to users (login by e-mail) + app_routes table for routes
-- uploaded from the MotoTracker Android app. Idempotent (safe to re-run).
--
-- email is nullable so existing accounts survive the ALTER; new self-service
-- registrations (register.php) always set it. app_routes stores whole named
-- rides owned by a user; UNIQUE(user_id, client_uuid) makes the app's batch
-- upload idempotent (retry from the sync queue upserts, never duplicates).

ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(190) UNIQUE NULL;

CREATE TABLE IF NOT EXISTS app_routes (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT NOT NULL,
    client_uuid   VARCHAR(64) NOT NULL,
    name          VARCHAR(190) NOT NULL,
    started_at    DATETIME NOT NULL,
    km            DOUBLE NOT NULL DEFAULT 0,
    dur_sec       INT NOT NULL DEFAULT 0,
    avg_kmh       DOUBLE NOT NULL DEFAULT 0,
    max_kmh       DOUBLE NOT NULL DEFAULT 0,
    path_json     LONGTEXT,
    payload_json  LONGTEXT NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_routes_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_user_client (user_id, client_uuid),
    INDEX idx_user_started (user_id, started_at)
);
