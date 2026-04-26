<?php
// Update a device's name and/or color. Omit a field to leave it unchanged.
// Name: 1..128 chars after trim. Color: #rrggbb.
include('../gps_track_config.php');
include('../auth.php');
include('_common.php');

auth_require_write();
dev_require_post();
header('Content-Type: application/json');

$device_id = (int)($_POST['device_id'] ?? 0);
if ($device_id <= 0) dev_bad_request('missing_device_id');

$dev = dev_load_owned($auth_conn, $device_id);

$name_raw  = $_POST['name']  ?? null;
$color_raw = $_POST['color'] ?? null;

$sets = [];
$types = '';
$vals = [];

if ($name_raw !== null) {
    $name = trim((string)$name_raw);
    if ($name === '' || mb_strlen($name) > 128) {
        dev_bad_request('invalid_name');
    }
    $sets[] = 'name = ?';
    $types .= 's';
    $vals[] = $name;
}

if ($color_raw !== null) {
    $color = trim((string)$color_raw);
    if (!preg_match('/^#[0-9a-fA-F]{6}$/', $color)) {
        dev_bad_request('invalid_color');
    }
    $sets[] = 'color = ?';
    $types .= 's';
    $vals[] = $color;
}

if (!$sets) dev_bad_request('nothing_to_update');

$sql = "UPDATE devices SET " . implode(', ', $sets) . " WHERE id = ?";
$types .= 'i';
$vals[] = $device_id;

$stmt = $auth_conn->prepare($sql);
$stmt->bind_param($types, ...$vals);
$ok = $stmt->execute();
$stmt->close();

if (!$ok) {
    http_response_code(500);
    echo json_encode(['error' => 'update_failed']);
    exit;
}

echo json_encode([
    'ok' => true,
    'device_id' => $device_id,
    'name' => $name_raw !== null ? $name : $dev['name'],
    'color' => $color_raw !== null ? $color : $dev['color'],
]);
$auth_conn->close();
