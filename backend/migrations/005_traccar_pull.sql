-- Dedup key for pull_from_traccar.py. Each raw row pulled from Traccar
-- carries its Traccar position id so the puller can INSERT IGNORE without
-- tracking per-device watermarks. NULL is allowed (for non-Traccar sources
-- like direct LilyGO ingest); MySQL unique indexes accept multiple NULLs.
--
-- Safe to re-run.

ALTER TABLE points
    ADD COLUMN IF NOT EXISTS traccar_pos_id BIGINT DEFAULT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_points_traccar
    ON points(device_id, traccar_pos_id);
