<?php
error_reporting(E_ALL);
ini_set('display_errors', 1);
include('gps_track_config.php');

// Accept both short and long parameter names for backward compatibility.
// `v` is now a device code (string, e.g. 'S_02' or 'HA_6') — resolved to devices.id.
$w_api_key   = $_GET['k']  ?? $_GET['api_key']     ?? '';
$device_code = $_GET['v']  ?? $_GET['vehicle_id']  ?? '';
$lat         = $_GET['la'] ?? $_GET['lat']         ?? '';
$lon         = $_GET['lo'] ?? $_GET['lon']         ?? '';
$speed       = $_GET['s']  ?? $_GET['speed']       ?? null;
$temperature = $_GET['t']  ?? $_GET['temperature'] ?? '';
$humidity    = $_GET['h']  ?? $_GET['humidyty']    ?? '';
$battery     = $_GET['b']  ?? $_GET['battery']     ?? '';
$timestamp   = $_GET['ts'] ?? $_GET['timestamp']   ?? null;

$log_file = 'log_file.log';
$log_entry = date('Y-m-d H:i:s') . " - " . http_build_query($_GET) . "\n";
file_put_contents($log_file, $log_entry, FILE_APPEND);

if ($w_api_key === '') {
    die("Invalid API Key");
}

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) {
    die("DB connect error");
}

// Resolve user by write API key.
$stmt = $conn->prepare("SELECT id, is_admin, active FROM users WHERE write_api_key = ?");
$stmt->bind_param("s", $w_api_key);
$stmt->execute();
$user_id = null;
$is_admin = 0;
$user_active = 0;
$stmt->bind_result($user_id, $is_admin, $user_active);
$stmt->fetch();
$stmt->close();

if (!$user_id) {
    die("Invalid API Key");
}

if ((int)$user_active === 0) {
    die("Account disabled");
}

// Look up device by code; must be active. Non-admins must own the device.
$stmt = $conn->prepare("SELECT id, user_id FROM devices WHERE code = ? AND active = 1");
$stmt->bind_param("s", $device_code);
$stmt->execute();
$device_id = null;
$device_owner = null;
$stmt->bind_result($device_id, $device_owner);
$stmt->fetch();
$stmt->close();

if (!$device_id) {
    die("Invalid or inactive device: " . htmlspecialchars($device_code));
}

if (!$is_admin && (int)$device_owner !== (int)$user_id) {
    die("Device not owned by this API key");
}

if ($timestamp !== null && $timestamp !== '') {
    $stmt = $conn->prepare("INSERT INTO points (device_id, lat, lon, speed, temperature, humidyty, battery, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    $stmt->bind_param("idddddds", $device_id, $lat, $lon, $speed, $temperature, $humidity, $battery, $timestamp);
} else if ($speed !== null && $speed !== '') {
    $stmt = $conn->prepare("INSERT INTO points (device_id, lat, lon, speed, temperature, humidyty, battery) VALUES (?, ?, ?, ?, ?, ?, ?)");
    $stmt->bind_param("idddddd", $device_id, $lat, $lon, $speed, $temperature, $humidity, $battery);
} else {
    $stmt = $conn->prepare("INSERT INTO points (device_id, lat, lon, temperature, humidyty, battery) VALUES (?, ?, ?, ?, ?, ?)");
    $stmt->bind_param("idddddd", $device_id, $lat, $lon, $temperature, $humidity, $battery);
}

$result = $stmt->execute();
echo $result ? "OK" : "ERR";

$stmt->close();
$conn->close();
