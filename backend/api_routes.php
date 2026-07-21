<?php
// Route ingest for the MotoTracker app. POST application/json — the whole
// recorded ride as sent by HttpGpStrackClient:
//   { id, name, dateEpochMs, bikeId?, km, durSec, avg, max, lean, elev, fuel,
//     wxJson?, pathJson?, speedJson?, elevProfileJson?, notes? }
//
// Auth: session cookie (login.php/register.php) via auth.php. Read-only API-key
// callers are rejected by auth_require_write(). Stored in app_routes owned by
// the session user; upsert on (user_id, client_uuid) so a retried upload from
// the app's sync queue never duplicates.
//
// Success: 200 {"ok":true,"route_id":N}

include('gps_track_config.php');
include('auth.php');       // 401s + exits if not authenticated; sets $current_user, $auth_conn
auth_require_write();      // 403s read-only-token callers

header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'method_not_allowed']);
    exit;
}

$raw = file_get_contents('php://input');
$r = json_decode($raw, true);
if (!is_array($r)) {
    http_response_code(400);
    echo json_encode(['error' => 'invalid_json']);
    exit;
}

$client_uuid = trim((string)($r['id'] ?? ''));
$name        = trim((string)($r['name'] ?? ''));
$date_ms     = $r['dateEpochMs'] ?? null;

if ($client_uuid === '' || $name === '' || !is_numeric($date_ms)) {
    http_response_code(400);
    echo json_encode(['error' => 'missing_fields', 'detail' => 'id/name/dateEpochMs required']);
    exit;
}

$user_id    = (int)$current_user['id'];
$started_at = date('Y-m-d H:i:s', (int)((int)$date_ms / 1000));
$km         = (float)($r['km']  ?? 0);
$dur_sec    = (int)  ($r['durSec'] ?? 0);
$avg        = (float)($r['avg'] ?? 0);
$max        = (float)($r['max'] ?? 0);
$path_json  = isset($r['pathJson']) ? (string)$r['pathJson'] : null;
$payload    = $raw; // store the full incoming object for the rich web view

$stmt = $auth_conn->prepare(
    "INSERT INTO app_routes
        (user_id, client_uuid, name, started_at, km, dur_sec, avg_kmh, max_kmh, path_json, payload_json)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
        name = VALUES(name), started_at = VALUES(started_at), km = VALUES(km),
        dur_sec = VALUES(dur_sec), avg_kmh = VALUES(avg_kmh), max_kmh = VALUES(max_kmh),
        path_json = VALUES(path_json), payload_json = VALUES(payload_json)"
);
$stmt->bind_param(
    "isssdiddss",
    $user_id, $client_uuid, $name, $started_at, $km, $dur_sec, $avg, $max, $path_json, $payload
);
if (!$stmt->execute()) {
    $stmt->close();
    http_response_code(500);
    echo json_encode(['error' => 'insert_failed']);
    exit;
}
// insert_id is the new row on INSERT, 0 on a pure UPDATE — resolve the real id.
$route_id = $stmt->insert_id;
$stmt->close();
if ($route_id === 0) {
    $sel = $auth_conn->prepare("SELECT id FROM app_routes WHERE user_id = ? AND client_uuid = ?");
    $sel->bind_param("is", $user_id, $client_uuid);
    $sel->execute();
    $route_id = (int)($sel->get_result()->fetch_assoc()['id'] ?? 0);
    $sel->close();
}

echo json_encode(['ok' => true, 'route_id' => (int)$route_id]);
