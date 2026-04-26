<?php
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

admin_require_post();
header('Content-Type: application/json');

$device_id   = (int)($_POST['device_id']   ?? 0);
$new_user_id = (int)($_POST['new_user_id'] ?? 0);

if ($device_id   <= 0) admin_bad_request('missing_device_id');
if ($new_user_id <= 0) admin_bad_request('missing_new_user_id');

// Verify target user exists — FK would catch it but we want a clean JSON error.
$chk = $auth_conn->prepare("SELECT 1 FROM users WHERE id = ?");
$chk->bind_param("i", $new_user_id);
$chk->execute();
$chk->store_result();
$user_exists = $chk->num_rows > 0;
$chk->close();

if (!$user_exists) {
    http_response_code(404);
    echo json_encode(['error' => 'target_user_not_found']);
    exit;
}

$stmt = $auth_conn->prepare("UPDATE devices SET user_id = ? WHERE id = ?");
$stmt->bind_param("ii", $new_user_id, $device_id);
$ok = $stmt->execute();
$affected = $stmt->affected_rows;
$stmt->close();

if (!$ok) {
    http_response_code(500);
    echo json_encode(['error' => 'update_failed']);
    exit;
}

if ($affected === 0) {
    // Could be no-op (already assigned to that user) or unknown device.
    $chk = $auth_conn->prepare("SELECT 1 FROM devices WHERE id = ?");
    $chk->bind_param("i", $device_id);
    $chk->execute();
    $chk->store_result();
    $dev_exists = $chk->num_rows > 0;
    $chk->close();
    if (!$dev_exists) {
        http_response_code(404);
        echo json_encode(['error' => 'device_not_found']);
        exit;
    }
}

echo json_encode(['ok' => true, 'device_id' => $device_id, 'new_user_id' => $new_user_id]);
$auth_conn->close();
