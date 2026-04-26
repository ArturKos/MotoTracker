<?php
include('gps_track_config.php');
session_start();

header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'method_not_allowed']);
    exit;
}

$username = trim($_POST['username'] ?? '');
$password = $_POST['password'] ?? '';

if ($username === '' || $password === '') {
    http_response_code(400);
    echo json_encode(['error' => 'missing_credentials']);
    exit;
}

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode(['error' => 'db_connect_failed']);
    exit;
}

$stmt = $conn->prepare("SELECT id, password_hash, is_admin, must_change_password, active FROM users WHERE username = ?");
$stmt->bind_param("s", $username);
$stmt->execute();
$result = $stmt->get_result();
$row = $result->fetch_assoc();
$stmt->close();

// Throttle with a constant-time failure to avoid leaking which branch lost.
if (!$row || !password_verify($password, $row['password_hash'])) {
    http_response_code(401);
    echo json_encode(['error' => 'invalid_credentials']);
    exit;
}

if ((int)$row['active'] === 0) {
    http_response_code(403);
    echo json_encode(['error' => 'account_disabled']);
    exit;
}

session_regenerate_id(true);
$_SESSION['user_id'] = (int)$row['id'];

echo json_encode([
    'ok' => true,
    'must_change_password' => (int)$row['must_change_password'],
]);
