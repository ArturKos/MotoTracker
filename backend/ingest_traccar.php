<?php
// Ingest shim for phone GPS trackers. Three transport styles in the wild:
//
//   1. OsmAnd style — GET with all params in the URL query string.
//      ?k=KEY&id=DEV&lat=..&lon=..&timestamp=<epoch>&speed=<m/s>&batt=<%>
//
//   2. Traccar Client (Android, older iOS) — POST with form-encoded body,
//      same field names as OsmAnd but speed in knots by default.
//      Flag `source=osmand` in the URL to treat speed as m/s instead.
//
//   3. Traccar Client (iOS ≥ 2025 / Transistor BackgroundGeolocation) —
//      POST with `application/json` body looking like:
//        {"location":{"coords":{"latitude":..,"longitude":..,"speed":<m/s>,
//                               "accuracy":..,"altitude":..,"heading":..},
//                     "timestamp":"2026-04-20T03:54:37.381Z",
//                     "battery":{"level":0.55,"is_charging":false}},
//         "device_id":"18638852"}
//      (`-1` means unknown, stored as NULL.)
//
// Device identifier must match an existing `devices.code` owned by the caller
// (admins bypass the ownership check). Emits HTTP 200 "OK" on success.

error_reporting(E_ALL);
ini_set('display_errors', 0);
include('gps_track_config.php');

$content_type = strtolower($_SERVER['CONTENT_TYPE'] ?? '');
$raw_body = file_get_contents('php://input');

$api_key = '';  $dev_code = '';
$lat = null;    $lon = null;
$epoch = null;  $speed_in = null;
$battery = null; $source = '';
$speed_unit = 'knots'; // default for Traccar Client

// URL query string always wins for api_key + source hint (Traccar Client
// sticks them in the URL even when the body is JSON).
if (isset($_GET['k']))       $api_key = $_GET['k'];
if (isset($_GET['api_key'])) $api_key = $_GET['api_key'];
if (isset($_GET['source']))  $source  = strtolower($_GET['source']);

if (strpos($content_type, 'application/json') !== false) {
    // JSON path — Transistor BackgroundGeolocation.
    $j = json_decode($raw_body, true);
    if (is_array($j)) {
        $coords = $j['location']['coords'] ?? [];
        $dev_code = (string)($j['device_id']
                    ?? $j['location']['device_id']
                    ?? $j['id']
                    ?? '');
        $lat = $coords['latitude']  ?? null;
        $lon = $coords['longitude'] ?? null;

        // speed = -1 means "unknown".
        $s = $coords['speed'] ?? null;
        if ($s !== null && $s >= 0) {
            $speed_in = $s;
            $speed_unit = 'ms'; // Transistor always reports m/s
        }

        // battery.level is a 0..1 fraction.
        if (isset($j['location']['battery']['level'])) {
            $battery = round(100 * (float)$j['location']['battery']['level']);
        }

        // ISO8601 → epoch.
        $ts_iso = $j['location']['timestamp'] ?? null;
        if ($ts_iso) {
            $t = strtotime($ts_iso);
            if ($t !== false) $epoch = $t;
        }
    }
} else {
    // Form-encoded or GET params.
    $body_parsed = [];
    if ($raw_body !== '' && strpos($content_type, 'application/x-www-form-urlencoded') !== false) {
        parse_str($raw_body, $body_parsed);
    }
    $P = array_merge($_REQUEST ?? [], $body_parsed);

    if ($api_key === '') $api_key = $P['k']       ?? $P['api_key']  ?? '';
    $dev_code  = (string)($P['id']        ?? $P['deviceid'] ?? $P['device'] ?? '');
    $lat       = $P['lat']       ?? null;
    $lon       = $P['lon']       ?? null;
    $epoch_raw = $P['timestamp'] ?? null;
    if ($epoch_raw !== null && $epoch_raw !== '' && ctype_digit((string)$epoch_raw)) {
        $epoch = (int)$epoch_raw;
    }
    $speed_in = $P['speed']   ?? null;
    $battery  = $P['batt']    ?? $P['battery'] ?? null;
    if ($source === '') $source = strtolower($P['source'] ?? '');
    $speed_unit = ($source === 'osmand') ? 'ms' : 'knots';
}

$log_entry = date('Y-m-d H:i:s') . " - traccar_shim - "
    . "ct=$content_type dev=$dev_code lat=$lat lon=$lon speed=$speed_in unit=$speed_unit batt=$battery ts=$epoch\n";
file_put_contents('log_file.log', $log_entry, FILE_APPEND);

if ($api_key === '' || $dev_code === '' || $lat === null || $lon === null || $lat === '' || $lon === '') {
    http_response_code(400);
    echo "Missing required params (k, id/device_id, lat/lon)";
    exit;
}

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) {
    http_response_code(500);
    echo "DB connect error";
    exit;
}

$stmt = $conn->prepare("SELECT id, is_admin, active FROM users WHERE write_api_key = ?");
$stmt->bind_param("s", $api_key);
$stmt->execute();
$user_id = null; $is_admin = 0; $user_active = 0;
$stmt->bind_result($user_id, $is_admin, $user_active);
$stmt->fetch();
$stmt->close();

if (!$user_id) {
    http_response_code(401);
    echo "Invalid API key";
    exit;
}

if ((int)$user_active === 0) {
    http_response_code(403);
    echo "Account disabled";
    exit;
}

$stmt = $conn->prepare("SELECT id, user_id FROM devices WHERE code = ? AND active = 1");
$stmt->bind_param("s", $dev_code);
$stmt->execute();
$device_id = null; $device_owner = null;
$stmt->bind_result($device_id, $device_owner);
$stmt->fetch();
$stmt->close();

if (!$device_id) {
    http_response_code(404);
    echo "Unknown device code: " . htmlspecialchars($dev_code);
    exit;
}
if (!$is_admin && (int)$device_owner !== (int)$user_id) {
    http_response_code(403);
    echo "Device not owned by this API key";
    exit;
}

// Normalize speed to km/h.
$speed_kmh = null;
if ($speed_in !== null && $speed_in !== '') {
    $s = (float)$speed_in;
    $speed_kmh = ($speed_unit === 'ms') ? $s * 3.6 : $s * 1.852;
}

$ts_sql = $epoch !== null ? date('Y-m-d H:i:s', (int)$epoch) : null;

$lat_f = (float)$lat;
$lon_f = (float)$lon;
$bat_f = ($battery !== null && $battery !== '') ? (float)$battery : null;

if ($ts_sql !== null) {
    $stmt = $conn->prepare(
        "INSERT INTO points (device_id, lat, lon, speed, battery, timestamp)
         VALUES (?, ?, ?, ?, ?, ?)"
    );
    $stmt->bind_param("idddds", $device_id, $lat_f, $lon_f, $speed_kmh, $bat_f, $ts_sql);
} else {
    $stmt = $conn->prepare(
        "INSERT INTO points (device_id, lat, lon, speed, battery)
         VALUES (?, ?, ?, ?, ?)"
    );
    $stmt->bind_param("idddd", $device_id, $lat_f, $lon_f, $speed_kmh, $bat_f);
}

$ok = $stmt->execute();
$stmt->close();
$conn->close();

echo $ok ? "OK" : "ERR";
