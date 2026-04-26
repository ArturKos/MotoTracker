<?php
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

admin_require_post();
header('Content-Type: application/json');

$user_id = (int)($_POST['user_id'] ?? 0);
$active  = isset($_POST['active']) ? (int)$_POST['active'] : -1;

if ($user_id <= 0)              admin_bad_request('missing_user_id');
if ($active !== 0 && $active !== 1) admin_bad_request('invalid_active');

// Refuse to disable yourself — saves admins from locking themselves out.
if ($user_id === (int)$current_user['id'] && $active === 0) {
    http_response_code(409);
    echo json_encode(['error' => 'cannot_disable_self']);
    exit;
}

$stmt = $auth_conn->prepare("UPDATE users SET active = ? WHERE id = ?");
$stmt->bind_param("ii", $active, $user_id);
$ok = $stmt->execute();
$affected = $stmt->affected_rows;
$stmt->close();

if (!$ok) {
    http_response_code(500);
    echo json_encode(['error' => 'update_failed']);
    exit;
}
if ($affected === 0) {
    // affected_rows=0 can mean "no such user" or "already at that value";
    // distinguish so the UI can give better feedback.
    $chk = $auth_conn->prepare("SELECT 1 FROM users WHERE id = ?");
    $chk->bind_param("i", $user_id);
    $chk->execute();
    $chk->store_result();
    $exists = $chk->num_rows > 0;
    $chk->close();
    if (!$exists) {
        http_response_code(404);
        echo json_encode(['error' => 'user_not_found']);
        exit;
    }
}

echo json_encode(['ok' => true, 'active' => $active]);
$auth_conn->close();
