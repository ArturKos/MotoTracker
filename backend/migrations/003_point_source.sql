-- Add provenance columns to points so the snap/interpolate worker can tell
-- raw GPS rows from snapped or interpolated ones, and can link an
-- interpolated intermediate back to the raw row it was sampled from.
--
-- Safe to re-run: uses IF NOT EXISTS on MariaDB 10.2+.

ALTER TABLE points
    ADD COLUMN IF NOT EXISTS source ENUM('raw','snapped','interpolated')
         NOT NULL DEFAULT 'raw',
    ADD COLUMN IF NOT EXISTS parent_id INT DEFAULT NULL;

-- Indexes exposed separately (MariaDB doesn't accept IF NOT EXISTS inline
-- for INDEX in ALTER TABLE on all versions).
CREATE INDEX IF NOT EXISTS idx_points_source ON points(source);
CREATE INDEX IF NOT EXISTS idx_points_parent ON points(parent_id);
