-- 008: per-resource visibility grants. An admin grants a specific account the
-- right to view a specific OTHER device's points (resource_type='device') or a
-- specific OTHER user's app routes (resource_type='app_user' → app_routes only,
-- NOT that user's devices). Idempotent (safe to re-run).

CREATE TABLE IF NOT EXISTS view_grants (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    grantee_user_id INT NOT NULL,
    resource_type   ENUM('device','app_user') NOT NULL,
    resource_id     INT NOT NULL,
    created_by      INT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vg_grantee FOREIGN KEY (grantee_user_id) REFERENCES users(id),
    UNIQUE KEY uq_grant (grantee_user_id, resource_type, resource_id),
    INDEX idx_grantee (grantee_user_id)
);
