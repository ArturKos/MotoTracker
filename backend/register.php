<?php
// Open self-service registration for the MotoTracker app.
//
// POST application/json OR form-encoded: { email, password, display_name? }
// - Validates e-mail format and a minimum password length.
// - Rejects a duplicate e-mail/username with 409.
// - Stores the password with password_hash() (bcrypt — a unique per-password
//   salt is generated and embedded in the hash; do NOT add a manual salt).
// - Generates write/read API keys, creates the user, and auto-logs-in
//   (session cookie) so the app can immediately upload routes.
//
// Success: 200 {"ok":true,"user_id":N}

include('gps_track_config.php');
session_start();

header('Content-Type: application/json');

const MIN_PASSWORD_LEN = 8;

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'method_not_allowed']);
    exit;
}

// Accept JSON body (the app) or form-encoded (browser/curl).
$raw = file_get_contents('php://input');
$data = [];
$ctype = strtolower($_SERVER['CONTENT_TYPE'] ?? '');
if (strpos($ctype, 'application/json') !== false) {
    $decoded = json_decode($raw, true);
    if (is_array($decoded)) $data = $decoded;
} else {
    $data = $_POST;
}

$email        = trim($data['email'] ?? '');
$password     = (string)($data['password'] ?? '');
$display_name = trim($data['display_name'] ?? '');
if ($display_name === '') $display_name = null;

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    http_response_code(400);
    echo json_encode(['error' => 'invalid_input', 'detail' => 'invalid_email']);
    exit;
}
if (strlen($password) < MIN_PASSWORD_LEN) {
    http_response_code(400);
    echo json_encode(['error' => 'invalid_input', 'detail' => 'weak_password']);
    exit;
}

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) {
    http_response_code(500);
    echo json_encode(['error' => 'db_connect_failed']);
    exit;
}

// username == e-mail (single identity); reject if either already taken.
$stmt = $conn->prepare("SELECT id FROM users WHERE email = ? OR username = ?");
$stmt->bind_param("ss", $email, $email);
$stmt->execute();
$exists = $stmt->get_result()->fetch_assoc();
$stmt->close();
if ($exists) {
    http_response_code(409);
    echo json_encode(['error' => 'email_taken']);
    exit;
}

$hash          = password_hash($password, PASSWORD_DEFAULT);
$write_api_key = bin2hex(random_bytes(16));
$read_api_key  = bin2hex(random_bytes(16));

$stmt = $conn->prepare(
    "INSERT INTO users (username, email, password_hash, display_name,
                        write_api_key, read_api_key, is_admin, must_change_password, active)
     VALUES (?, ?, ?, ?, ?, ?, 0, 0, 1)"
);
$stmt->bind_param("ssssss", $email, $email, $hash, $display_name, $write_api_key, $read_api_key);
if (!$stmt->execute()) {
    // Unique-constraint race → treat as taken; anything else is a server error.
    $dup = ($conn->errno === 1062);
    $stmt->close();
    http_response_code($dup ? 409 : 500);
    echo json_encode(['error' => $dup ? 'email_taken' : 'insert_failed']);
    exit;
}
$user_id = $stmt->insert_id;
$stmt->close();

// Auto-login.
session_regenerate_id(true);
$_SESSION['user_id'] = (int)$user_id;

echo json_encode(['ok' => true, 'user_id' => (int)$user_id]);
