<?php
// Shared helpers for user-facing device endpoints. Every file in this dir
// should `include('_common.php')` after auth.php.

function dev_bad_request($msg) {
    http_response_code(400);
    header('Content-Type: application/json');
    echo json_encode(['error' => $msg]);
    exit;
}

function dev_require_post() {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        http_response_code(405);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'method_not_allowed']);
        exit;
    }
}

// Load device by id and confirm the current user may modify it. Admins
// bypass ownership. Returns the device row; exits 403/404 otherwise.
function dev_load_owned($conn, $device_id) {
    global $current_user;
    $stmt = $conn->prepare("SELECT id, code, name, color, user_id, active FROM devices WHERE id = ?");
    $stmt->bind_param("i", $device_id);
    $stmt->execute();
    $row = $stmt->get_result()->fetch_assoc();
    $stmt->close();

    if (!$row) {
        http_response_code(404);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'device_not_found']);
        exit;
    }
    if (!$current_user['is_admin'] && (int)$row['user_id'] !== (int)$current_user['id']) {
        http_response_code(403);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'not_your_device']);
        exit;
    }
    return $row;
}
