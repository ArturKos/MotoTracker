<?php
// POST grant_id — revoke a single view_grant by its id.
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

admin_require_post();
header('Content-Type: application/json');

$grant_id = (int)($_POST['grant_id'] ?? 0);
if ($grant_id <= 0) admin_bad_request('missing_grant_id');

$stmt = $auth_conn->prepare("DELETE FROM view_grants WHERE id = ?");
$stmt->bind_param("i", $grant_id);
$ok = $stmt->execute();
$affected = $stmt->affected_rows;
$stmt->close();
if (!$ok) { http_response_code(500); echo json_encode(['error' => 'delete_failed']); exit; }

echo json_encode(['ok' => true, 'removed' => $affected]);
$auth_conn->close();
