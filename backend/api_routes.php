<?php
// Route ingest dla aplikacji MotoTracker (PIVOT: telefon jako device).
// POST application/json — cała nagrana trasa + tożsamość urządzenia:
//   { id, name, dateEpochMs, deviceCode, deviceName?, km, durSec, avg, max,
//     lean?, elev?, fuel?, bikeId?, wxJson?, pathJson?, speedJson?, notes? }
//
// Efekt: (1) ensure-device po deviceCode (auto-tworzenie telefonu),
//        (2) upsert do rides po (user_id, client_uuid),
//        (3) explode pathJson -> points (source='raw', ride_id) — telefon w glownym widoku.
// Auth: sesja (login/register) lub write_api_key Bearer via auth.php + auth_require_write().
// Sukces: 200 {"ok":true,"ride_id":N,"device_id":M,"points_inserted":K}

include('gps_track_config.php');
include('auth.php');       // 401+exit gdy brak auth; ustawia $current_user, $auth_conn
include('route_points.php');
auth_require_write();      // 403 dla read-only-token

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
$device_code = trim((string)($r['deviceCode'] ?? ''));
$device_name = trim((string)($r['deviceName'] ?? ''));

if ($client_uuid === '' || $name === '' || !is_numeric($date_ms) || $device_code === '') {
    http_response_code(400);
    echo json_encode(['error' => 'missing_fields', 'detail' => 'id/name/dateEpochMs/deviceCode required']);
    exit;
}

$user_id    = (int)$current_user['id'];
$is_admin   = (int)($current_user['is_admin'] ?? 0);
$started_at = date('Y-m-d H:i:s', (int)((int)$date_ms / 1000));
$km         = (float)($r['km'] ?? 0);
$dur_sec    = (int)  ($r['durSec'] ?? 0);
$avg        = (float)($r['avg'] ?? 0);
$max        = (float)($r['max'] ?? 0);
$path_json  = isset($r['pathJson']) ? (string)$r['pathJson'] : null;
$payload    = $raw; // pełny obiekt (rich: lean/fuel/wx/notes) do rides.payload_json

// ── 1. Ensure-device ────────────────────────────────────────────────────────
$stmt = $auth_conn->prepare("SELECT id, user_id FROM devices WHERE code = ?");
$stmt->bind_param("s", $device_code);
$stmt->execute();
$dev_id = null; $dev_owner = null;
$stmt->bind_result($dev_id, $dev_owner);
$stmt->fetch();
$stmt->close();

if ($dev_id) {
    if (!$is_admin && (int)$dev_owner !== $user_id) {
        http_response_code(409);
        echo json_encode(['error' => 'device_owned_by_other']);
        exit;
    }
    if ($device_name !== '') {
        $u = $auth_conn->prepare("UPDATE devices SET name = ? WHERE id = ?");
        $u->bind_param("si", $device_name, $dev_id);
        $u->execute();
        $u->close();
    }
} else {
    // Kolor z małej palety, indeks po liczbie istniejących urządzeń (deterministyczny-ish).
    $palette = ['#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22', '#16a085'];
    $cnt = (int)($auth_conn->query("SELECT COUNT(*) AS c FROM devices")->fetch_assoc()['c'] ?? 0);
    $color = $palette[$cnt % count($palette)];
    $nm = $device_name !== '' ? $device_name : 'Telefon';
    $ins = $auth_conn->prepare("INSERT INTO devices (code, name, user_id, color, active) VALUES (?, ?, ?, ?, 1)");
    $ins->bind_param("ssis", $device_code, $nm, $user_id, $color);
    if (!$ins->execute()) {
        $ins->close();
        http_response_code(500);
        echo json_encode(['error' => 'device_create_failed']);
        exit;
    }
    $dev_id = $ins->insert_id;
    $ins->close();
}
$dev_id = (int)$dev_id;

// ── 2. Upsert ride ────────────────────────────────────────────────────────────
$stmt = $auth_conn->prepare(
    "INSERT INTO rides
        (user_id, client_uuid, name, started_at, km, dur_sec, avg_kmh, max_kmh, path_json, payload_json, device_id)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
        name = VALUES(name), started_at = VALUES(started_at), km = VALUES(km),
        dur_sec = VALUES(dur_sec), avg_kmh = VALUES(avg_kmh), max_kmh = VALUES(max_kmh),
        path_json = VALUES(path_json), payload_json = VALUES(payload_json), device_id = VALUES(device_id)"
);
$stmt->bind_param(
    "isssdiddssi",
    $user_id, $client_uuid, $name, $started_at, $km, $dur_sec, $avg, $max, $path_json, $payload, $dev_id
);
if (!$stmt->execute()) {
    $stmt->close();
    http_response_code(500);
    echo json_encode(['error' => 'insert_failed']);
    exit;
}
$ride_id = $stmt->insert_id;
$stmt->close();
if ($ride_id === 0) {
    $sel = $auth_conn->prepare("SELECT id FROM rides WHERE user_id = ? AND client_uuid = ?");
    $sel->bind_param("is", $user_id, $client_uuid);
    $sel->execute();
    $ride_id = (int)($sel->get_result()->fetch_assoc()['id'] ?? 0);
    $sel->close();
}
$ride_id = (int)$ride_id;

// ── 3. Explode pathJson -> points (transakcja, idempotentnie po ride_id) ───────
$points = parse_path_points($path_json);
$inserted = 0;
$auth_conn->begin_transaction();
try {
    // Usuń pochodne (snapped/interpolated) tego przejazdu, potem raw.
    $d1 = $auth_conn->prepare(
        "DELETE FROM points WHERE parent_id IN (SELECT id FROM (SELECT id FROM points WHERE ride_id = ?) t)"
    );
    $d1->bind_param("i", $ride_id);
    if (!$d1->execute()) { $d1->close(); throw new \RuntimeException('delete derived points failed'); }
    $d1->close();

    $d2 = $auth_conn->prepare("DELETE FROM points WHERE ride_id = ?");
    $d2->bind_param("i", $ride_id);
    if (!$d2->execute()) { $d2->close(); throw new \RuntimeException('delete raw points failed'); }
    $d2->close();

    if ($points) {
        $insP = $auth_conn->prepare(
            "INSERT INTO points (device_id, timestamp, lat, lon, speed, altitude, source, ride_id)
             VALUES (?, ?, ?, ?, ?, ?, 'raw', ?)"
        );
        foreach ($points as $p) {
            $ts = $p['ts_ms'] !== null ? date('Y-m-d H:i:s', (int)($p['ts_ms'] / 1000)) : $started_at;
            $insP->bind_param("isddddi", $dev_id, $ts, $p['lat'], $p['lon'], $p['speed'], $p['ele'], $ride_id);
            if (!$insP->execute()) { $insP->close(); throw new \RuntimeException('insert point failed'); }
            $inserted++;
        }
        $insP->close();
    }
    $auth_conn->commit();
} catch (\Throwable $e) {
    $auth_conn->rollback();
    http_response_code(500);
    echo json_encode(['error' => 'points_write_failed']);
    exit;
}

echo json_encode(['ok' => true, 'ride_id' => $ride_id, 'device_id' => $dev_id, 'points_inserted' => $inserted]);
