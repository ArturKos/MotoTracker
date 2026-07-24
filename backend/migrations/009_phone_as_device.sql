-- 009: pivot "telefon jako device".
-- points += altitude, ride_id; app_routes -> rides (+device_id).
-- Idempotentne (IF NOT EXISTS + information_schema guard), jak 003-008.

-- 1. Kolumny per-punkt w points.
ALTER TABLE points
    ADD COLUMN IF NOT EXISTS altitude DOUBLE DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS ride_id  INT    DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_points_ride ON points(ride_id);

-- 2. app_routes -> rides: rename tylko gdy legacy istnieje, a rides jeszcze nie.
SET @has_app_routes = (SELECT COUNT(*) FROM information_schema.tables
                       WHERE table_schema = DATABASE() AND table_name = 'app_routes');
SET @has_rides = (SELECT COUNT(*) FROM information_schema.tables
                  WHERE table_schema = DATABASE() AND table_name = 'rides');
SET @sql = IF(@has_app_routes > 0 AND @has_rides = 0,
    'RENAME TABLE app_routes TO rides', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. Świeży install: utwórz rides jeśli nadal nieobecna (schemat = app_routes + device_id).
CREATE TABLE IF NOT EXISTS rides (
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
    device_id     INT DEFAULT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_routes_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_user_client (user_id, client_uuid),
    INDEX idx_user_started (user_id, started_at)
);

-- 4. device_id na rides (idempotentne; pokrywa gałąź z rename).
ALTER TABLE rides ADD COLUMN IF NOT EXISTS device_id INT DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_rides_device ON rides(device_id);
