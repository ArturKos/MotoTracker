<?php
// Included by every protected endpoint. Expects gps_track_config.php to
// already be loaded so $gps_db_* are available. Returns JSON 401 and exits
// if the caller isn't authenticated.
//
// Two auth paths are supported:
//   1. Session cookie (login.php) — full read+write access.
//   2. Read-only API key (?token=… or Authorization: Bearer …) matching
//      users.read_api_key. Sets $auth_readonly = true; mutating endpoints
//      must call auth_require_write() to reject these callers.
//
// On success: $current_user, $auth_conn (mysqli), $auth_readonly are set.

session_start();

function auth_fail($msg) {
    http_response_code(401);
    header('Content-Type: application/json');
    echo json_encode(['error' => $msg]);
    exit;
}

function auth_require_write() {
    global $auth_readonly;
    if (!empty($auth_readonly)) {
        http_response_code(403);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'read_only_token']);
        exit;
    }
}

$auth_conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($auth_conn->connect_error) {
    http_response_code(500);
    echo json_encode(['error' => 'db_connect_failed']);
    exit;
}

$auth_readonly = false;
$current_user  = null;

// --- Path 1: read-only token ---
$token = '';
if (!empty($_GET['token'])) {
    $token = trim($_GET['token']);
} else {
    $hdr = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (stripos($hdr, 'Bearer ') === 0) $token = trim(substr($hdr, 7));
}

if ($token !== '' && preg_match('/^[a-f0-9]{32,64}$/i', $token)) {
    // Write-capable key first: a users.write_api_key grants full read+write.
    // The MotoTracker app stores it at register/login and sends it as a Bearer
    // token so background route upload works without a (expiring) session.
    $stmt = $auth_conn->prepare(
        "SELECT id, username, display_name, is_admin, must_change_password, active, write_api_key
         FROM users WHERE write_api_key = ?"
    );
    $stmt->bind_param("s", $token);
    $stmt->execute();
    $current_user = $stmt->get_result()->fetch_assoc();
    $stmt->close();

    if ($current_user) {
        $auth_readonly = false;
    } else {
        // Fall back to a read-only key (users.read_api_key).
        $stmt = $auth_conn->prepare(
            "SELECT id, username, display_name, is_admin, must_change_password, active, write_api_key
             FROM users WHERE read_api_key = ?"
        );
        $stmt->bind_param("s", $token);
        $stmt->execute();
        $current_user = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if ($current_user) $auth_readonly = true;
    }
}

// --- Path 2: session cookie ---
if (!$current_user) {
    if (empty($_SESSION['user_id'])) auth_fail('unauthorized');

    $stmt = $auth_conn->prepare(
        "SELECT id, username, display_name, is_admin, must_change_password, active, write_api_key
         FROM users WHERE id = ?"
    );
    $uid = (int)$_SESSION['user_id'];
    $stmt->bind_param("i", $uid);
    $stmt->execute();
    $current_user = $stmt->get_result()->fetch_assoc();
    $stmt->close();

    if (!$current_user) {
        session_destroy();
        auth_fail('user_not_found');
    }
}

$current_user['is_admin']             = (int)$current_user['is_admin'];
$current_user['must_change_password'] = (int)$current_user['must_change_password'];
$current_user['active']               = (int)$current_user['active'];
$current_user['id']                   = (int)$current_user['id'];

if (!$current_user['active']) {
    if (!$auth_readonly) session_destroy();
    auth_fail('account_disabled');
}

// Helper: WHERE clause fragment that restricts a query joining `devices d`
// to just the rows the current user is allowed to see. Admins see all.
// Read-only token callers are scoped to their user even if the user is admin
// (the token is for embedding a single user's view, not for spying on others).
function device_user_filter() {
    global $current_user, $auth_readonly;
    if ($current_user['is_admin'] && !$auth_readonly) return "";
    return " AND d.user_id = " . $current_user['id'];
}
