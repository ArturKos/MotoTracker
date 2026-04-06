<?php
error_reporting(E_ALL);
ini_set('display_errors', 1);
include('gps_track_config.php');

// Accept both short and long parameter names for backward compatibility
$w_api_key   = $_GET['k']  ?? $_GET['api_key']     ?? '';
$table       = $_GET['v']  ?? $_GET['vehicle_id']   ?? '';
$lat         = $_GET['la'] ?? $_GET['lat']          ?? '';
$lon         = $_GET['lo'] ?? $_GET['lon']          ?? '';
$speed       = $_GET['s']  ?? $_GET['speed']        ?? null;
$temperature = $_GET['t']  ?? $_GET['temperature']  ?? '';
$humidity    = $_GET['h']  ?? $_GET['humidyty']     ?? '';
$battery     = $_GET['b']  ?? $_GET['battery']      ?? '';
$timestamp   = $_GET['ts'] ?? $_GET['timestamp']    ?? null;

// Log request (compact)
$log_file = 'log_file.log';
$log_entry = date('Y-m-d H:i:s') . " - " . http_build_query($_GET) . "\n";
file_put_contents($log_file, $log_entry, FILE_APPEND);

// Whitelist allowed table names
$allowed_tables = ['S_02'];
if (!in_array($table, $allowed_tables, true)) {
    die("Invalid vehicle_id");
}

if ($w_api_key !== $gps_db_write_api_key) {
    die("Invalid API Key");
}

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);

if ($timestamp !== null && $timestamp !== '') {
    // Use GPS timestamp from device (more accurate than server time)
    $stmt = $conn->prepare("INSERT INTO $table (lat, lon, speed, temperature, humidyty, battery, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)");
    $stmt->bind_param("dddddds", $lat, $lon, $speed, $temperature, $humidity, $battery, $timestamp);
} else if ($speed !== null && $speed !== '') {
    // Has speed but no timestamp
    $stmt = $conn->prepare("INSERT INTO $table (lat, lon, speed, temperature, humidyty, battery) VALUES (?, ?, ?, ?, ?, ?)");
    $stmt->bind_param("dddddd", $lat, $lon, $speed, $temperature, $humidity, $battery);
} else {
    // Legacy format without speed/timestamp
    $stmt = $conn->prepare("INSERT INTO $table (lat, lon, temperature, humidyty, battery) VALUES (?, ?, ?, ?, ?)");
    $stmt->bind_param("ddddd", $lat, $lon, $temperature, $humidity, $battery);
}

$result = $stmt->execute();

if ($result === false) {
    echo "ERR";
} else {
    echo "OK";
}

$stmt->close();
$conn->close();
?>
