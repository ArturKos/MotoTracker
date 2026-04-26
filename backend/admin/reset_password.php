<?php
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

admin_require_post();
header('Content-Type: application/json');

$user_id      = (int)($_POST['user_id'] ?? 0);
$new_password = $_POST['new_password'] ?? '';

if ($user_id <= 0) admin_bad_request('missing_user_id');
if (strlen($new_password) < 8) admin_bad_request('password_too_short');

$hash = password_hash($new_password, PASSWORD_BCRYPT);
$stmt = $auth_conn->prepare(
    "UPDATE users SET password_hash = ?, must_change_password = 1 WHERE id = ?"
);
$stmt->bind_param("si", $hash, $user_id);
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

echo json_encode(['ok' => true, 'must_change_password' => 1]);
$auth_conn->close();
