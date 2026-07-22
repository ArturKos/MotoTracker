<?php
// POST grantee, resource_type ('device'|'app_user'), resource_id.
// Grants an account visibility of one resource (device points / another user's
// app routes). Idempotent — re-adding an existing grant is a no-op success.
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

admin_require_post();
header('Content-Type: application/json');

$grantee       = (int)($_POST['grantee'] ?? 0);
$resource_type = trim($_POST['resource_type'] ?? '');
$resource_id   = (int)($_POST['resource_id'] ?? 0);

if ($grantee <= 0)                                          admin_bad_request('missing_grantee');
if (!in_array($resource_type, ['device', 'app_user'], true)) admin_bad_request('bad_resource_type');
if ($resource_id <= 0)                                      admin_bad_request('missing_resource_id');

// Grantee must exist.
$chk = $auth_conn->prepare("SELECT 1 FROM users WHERE id = ?");
$chk->bind_param("i", $grantee);
$chk->execute(); $chk->store_result();
if ($chk->num_rows === 0) { $chk->close(); http_response_code(404); echo json_encode(['error' => 'grantee_not_found']); exit; }
$chk->close();

// Resource must exist (device row / user row).
$table = $resource_type === 'device' ? 'devices' : 'users';
$chk = $auth_conn->prepare("SELECT 1 FROM $table WHERE id = ?");
$chk->bind_param("i", $resource_id);
$chk->execute(); $chk->store_result();
if ($chk->num_rows === 0) { $chk->close(); http_response_code(404); echo json_encode(['error' => 'resource_not_found']); exit; }
$chk->close();

$created_by = (int)$current_user['id'];
$stmt = $auth_conn->prepare(
    "INSERT INTO view_grants (grantee_user_id, resource_type, resource_id, created_by)
     VALUES (?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE created_by = VALUES(created_by)"
);
$stmt->bind_param("isii", $grantee, $resource_type, $resource_id, $created_by);
$ok = $stmt->execute();
$stmt->close();
if (!$ok) { http_response_code(500); echo json_encode(['error' => 'insert_failed']); exit; }

echo json_encode(['ok' => true]);
$auth_conn->close();
