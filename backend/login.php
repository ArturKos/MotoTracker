<?php
include('gps_track_config.php');
session_start();

header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'method_not_allowed']);
    exit;
}

// Accept JSON body (the app posts application/json) or form-encoded (web).
$raw = file_get_contents('php://input');
$data = [];
$ctype = strtolower($_SERVER['CONTENT_TYPE'] ?? '');
if (strpos($ctype, 'application/json') !== false) {
    $decoded = json_decode($raw, true);
    if (is_array($decoded)) $data = $decoded;
} else {
    $data = $_POST;
}

// The app logs in by e-mail; the existing web front sends `username`.
// Accept either — the account's username == its e-mail for app-registered users.
$login    = trim($data['email'] ?? $data['username'] ?? '');
$password = (string)($data['password'] ?? '');

if ($login === '' || $password === '') {
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

$stmt = $conn->prepare("SELECT id, password_hash, is_admin, must_change_password, active, write_api_key FROM users WHERE email = ? OR username = ?");
$stmt->bind_param("ss", $login, $login);
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
    // For the app: authenticate background uploads with a Bearer token instead
    // of relying on the (expiring) session cookie.
    'write_api_key' => $row['write_api_key'],
]);
