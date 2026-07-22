<?php
// Read app-recorded routes (app_routes) visible to the caller.
//   GET (no ?id)   -> list: id, user_id, name, started_at, km, dur_sec, avg_kmh, max_kmh
//   GET ?id=N      -> detail: the above + path_json + payload_json
// Visibility: own routes + routes of users granted via view_grants
// (resource_type='app_user'); admins see all. Session or API-key auth via auth.php.

include('gps_track_config.php');
include('auth.php');   // 401s + exits if unauthenticated; sets $current_user, $auth_conn

header('Content-Type: application/json');

$visible = app_routes_user_ids();               // ints, always includes self
$in = implode(',', array_map('intval', $visible));

$id = isset($_GET['id']) ? (int)$_GET['id'] : 0;

if ($id > 0) {
    // Detail — only if the route is within the caller's visible set.
    $stmt = $auth_conn->prepare(
        "SELECT id, user_id, name, started_at, km, dur_sec, avg_kmh, max_kmh, path_json, payload_json
         FROM app_routes WHERE id = ? AND user_id IN ($in)"
    );
    $stmt->bind_param("i", $id);
    $stmt->execute();
    $row = $stmt->get_result()->fetch_assoc();
    $stmt->close();
    if (!$row) {
        http_response_code(404);
        echo json_encode(['error' => 'not_found']);
        exit;
    }
    echo json_encode($row);
    exit;
}

// List — newest first.
$res = $auth_conn->query(
    "SELECT id, user_id, name, started_at, km, dur_sec, avg_kmh, max_kmh
     FROM app_routes WHERE user_id IN ($in) ORDER BY started_at DESC"
);
$routes = [];
if ($res) { while ($r = $res->fetch_assoc()) $routes[] = $r; }
echo json_encode(['routes' => $routes]);
