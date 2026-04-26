<?php
// Included by every endpoint under backend/admin/. Bails out with 403 unless
// the caller is both authenticated and has is_admin=1. Must be included AFTER
// gps_track_config.php + auth.php so $current_user + $auth_conn are set.

// Admin endpoints always require a real session — read-only tokens never
// reach the admin surface, even if the token belongs to an admin user.
if (!empty($auth_readonly) || empty($current_user) || (int)$current_user['is_admin'] !== 1) {
    http_response_code(403);
    header('Content-Type: application/json');
    echo json_encode(['error' => 'admin_only']);
    exit;
}

function admin_bad_request($msg) {
    http_response_code(400);
    header('Content-Type: application/json');
    echo json_encode(['error' => $msg]);
    exit;
}

function admin_require_post() {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        http_response_code(405);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'method_not_allowed']);
        exit;
    }
}
