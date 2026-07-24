<?php
// Jednorazowy, idempotentny backfill: konwertuje istniejące wiersze `rides`
// (dawne app_routes) na wiersze `points` pod syntetycznym device'em telefonu.
// Uruchamiać z CLI:  php backend/tools/backfill_rides_to_points.php
//
// Stary model app_routes był per-user (brak per-install), więc tworzymy jeden
// device "Telefon (import)" na użytkownika (code = app-import-<user_id>).

require __DIR__ . '/../gps_track_config.php';
require __DIR__ . '/../route_points.php';

$conn = new mysqli($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) { fwrite(STDERR, "DB connect error\n"); exit(1); }

// Cache: user_id -> device_id (import).
$importDev = [];
function import_device_for_user(mysqli $conn, array &$cache, int $userId): int {
    if (isset($cache[$userId])) return $cache[$userId];
    $code = 'app-import-' . $userId;
    $sel = $conn->prepare("SELECT id FROM devices WHERE code = ?");
    $sel->bind_param("s", $code); $sel->execute();
    $id = (int)($sel->get_result()->fetch_assoc()['id'] ?? 0); $sel->close();
    if ($id === 0) {
        $name = 'Telefon (import)';
        $color = '#95a5a6';
        $ins = $conn->prepare("INSERT INTO devices (code, name, user_id, color, active) VALUES (?, ?, ?, ?, 1)");
        $ins->bind_param("ssis", $code, $name, $userId, $color);
        $ins->execute(); $id = $ins->insert_id; $ins->close();
    }
    $cache[$userId] = $id;
    return $id;
}

$rows = $conn->query("SELECT id, user_id, started_at, path_json, payload_json FROM rides ORDER BY id");
$ridesN = 0; $ptsN = 0; $skipped = 0;
while ($ride = $rows->fetch_assoc()) {
    $rideId  = (int)$ride['id'];
    $userId  = (int)$ride['user_id'];
    $started = $ride['started_at'] ?: date('Y-m-d H:i:s');
    $ridesN++;

    // Path source: prefer the path_json column; fall back to payload_json.pathJson.
    // Legacy rides uploaded before path_json was populated keep the path (if any)
    // only inside the raw request body stored in payload_json.
    $pathStr = $ride['path_json'];
    if ($pathStr === null || $pathStr === '') {
        $pl = json_decode((string)$ride['payload_json'], true);
        if (is_array($pl) && !empty($pl['pathJson'])) $pathStr = (string)$pl['pathJson'];
    }
    $points = parse_path_points($pathStr);

    if (!$points) {
        // No coordinates to explode → don't create an empty import device or link the ride.
        $skipped++;
        continue;
    }

    $devId = import_device_for_user($conn, $importDev, $userId);
    $conn->query("UPDATE rides SET device_id = $devId WHERE id = $rideId AND device_id IS NULL");

    // Idempotencja: skasuj najpierw pochodne (snapped/interpolated po parent_id), potem raw.
    $delD = $conn->prepare(
        "DELETE FROM points WHERE parent_id IN (SELECT id FROM (SELECT id FROM points WHERE ride_id = ?) t)"
    );
    $delD->bind_param("i", $rideId); $delD->execute(); $delD->close();
    $del = $conn->prepare("DELETE FROM points WHERE ride_id = ?");
    $del->bind_param("i", $rideId); $del->execute(); $del->close();

    $insP = $conn->prepare(
        "INSERT INTO points (device_id, timestamp, lat, lon, speed, altitude, source, ride_id)
         VALUES (?, ?, ?, ?, ?, ?, 'raw', ?)"
    );
    foreach ($points as $p) {
        $ts = $p['ts_ms'] !== null ? date('Y-m-d H:i:s', (int)($p['ts_ms'] / 1000)) : $started;
        $insP->bind_param("isddddi", $devId, $ts, $p['lat'], $p['lon'], $p['speed'], $p['ele'], $rideId);
        $insP->execute(); $ptsN++;
    }
    $insP->close();
}
echo "Backfill: $ridesN rides ($skipped bez współrzędnych, pominięte) -> "
   . count($importDev) . " device(ów) importu, $ptsN points\n";
$conn->close();
