-- Multi-device schema migration for MotoTracker.
-- Creates devices and unified points tables, migrates existing S_02 rows.

CREATE TABLE IF NOT EXISTS devices (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    color VARCHAR(16) NOT NULL DEFAULT '#3498db',
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS points (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_id INT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lat DOUBLE NOT NULL,
    lon DOUBLE NOT NULL,
    speed DOUBLE DEFAULT NULL,
    temperature DOUBLE DEFAULT NULL,
    humidyty DOUBLE DEFAULT NULL,
    battery DOUBLE DEFAULT NULL,
    CONSTRAINT fk_points_device FOREIGN KEY (device_id) REFERENCES devices(id),
    INDEX idx_device_timestamp (device_id, timestamp)
);

-- Seed devices: existing LilyGO tracker and all current Traccar devices.
INSERT INTO devices (id, code, name, color) VALUES
    (1, 'S_02', 'LilyGO Main', '#e74c3c'),
    (2, 'HA_1', 'Realme',      '#3498db'),
    (3, 'HA_2', 'Huawai',      '#2ecc71'),
    (4, 'HA_3', 'Yamaha FJR',  '#f39c12'),
    (5, 'HA_4', 'LilyGO HA',   '#f1c40f'),
    (6, 'HA_5', 'iPhone17',    '#9b59b6'),
    (7, 'HA_6', 'Sino ST-901L','#1abc9c')
ON DUPLICATE KEY UPDATE name=VALUES(name), color=VALUES(color);

-- Migrate legacy S_02 data if the table still exists (upgrade path from
-- the pre-multi-device install). On a fresh install this is a no-op.
SET @has_s02 = (SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'S_02');

SET @sql = IF(@has_s02 > 0,
    'INSERT INTO points (device_id, timestamp, lat, lon, speed, temperature, humidyty, battery)
     SELECT 1, timestamp, lat, lon, speed, temperature, humidyty, battery FROM S_02',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_s02 > 0,
    'RENAME TABLE S_02 TO S_02_backup',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
