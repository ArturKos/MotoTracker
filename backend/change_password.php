<?php
include('gps_track_config.php');
include('auth.php');

auth_require_write();
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'method_not_allowed']);
    exit;
}

$current_password = $_POST['current_password'] ?? '';
$new_password     = $_POST['new_password'] ?? '';

if (strlen($new_password) < 8) {
    http_response_code(400);
    echo json_encode(['error' => 'password_too_short']);
    exit;
}

$stmt = $auth_conn->prepare("SELECT password_hash FROM users WHERE id = ?");
$stmt->bind_param("i", $current_user['id']);
$stmt->execute();
$stmt->bind_result($existing_hash);
$stmt->fetch();
$stmt->close();

// Skip current-password check if the account is flagged must_change_password
// (first-login flow for seeded accounts).
if (!$current_user['must_change_password']) {
    if (!password_verify($current_password, $existing_hash)) {
        http_response_code(401);
        echo json_encode(['error' => 'wrong_current_password']);
        exit;
    }
}

$new_hash = password_hash($new_password, PASSWORD_BCRYPT);
$stmt = $auth_conn->prepare(
    "UPDATE users SET password_hash = ?, must_change_password = 0 WHERE id = ?"
);
$stmt->bind_param("si", $new_hash, $current_user['id']);
$stmt->execute();
$stmt->close();

echo json_encode(['ok' => true]);
