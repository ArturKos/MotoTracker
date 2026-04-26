<?php
// Regenerate the read-only view token (read_api_key) for a user.
// The token grants password-less access to that user's read endpoints —
// suitable for embedding the dashboard in Home Assistant etc. The previous
// token is invalidated immediately.
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

admin_require_post();
header('Content-Type: application/json');

$user_id = (int)($_POST['user_id'] ?? 0);
if ($user_id <= 0) admin_bad_request('missing_user_id');

$new_key = bin2hex(random_bytes(16)); // 32 hex chars

$stmt = $auth_conn->prepare("UPDATE users SET read_api_key = ? WHERE id = ?");
$stmt->bind_param("si", $new_key, $user_id);
$ok = $stmt->execute();
$affected = $stmt->affected_rows;
$stmt->close();

if (!$ok) {
    http_response_code(500);
    echo json_encode(['error' => 'update_failed']);
    exit;
}
if ($affected === 0) {
    http_response_code(404);
    echo json_encode(['error' => 'user_not_found']);
    exit;
}

echo json_encode(['ok' => true, 'read_api_key' => $new_key]);
$auth_conn->close();
