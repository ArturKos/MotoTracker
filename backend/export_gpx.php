<?php
include('gps_track_config.php');
include('auth.php');

$date = isset($_GET['date']) && $_GET['date'] !== '' ? $_GET['date'] : date('Y-m-d');
$device_code = $_GET['device'] ?? '';
$user_filter = device_user_filter();

if ($device_code === '') {
    http_response_code(400);
    echo "Missing device code";
    exit;
}

$sql = "SELECT p.lat, p.lon, p.timestamp, p.temperature, d.name AS device_name
        FROM points p
        JOIN devices d ON p.device_id = d.id
        WHERE DATE(p.timestamp) = ? AND d.code = ? AND d.active = 1 $user_filter
        ORDER BY p.timestamp ASC";
$stmt = $auth_conn->prepare($sql);
$stmt->bind_param("ss", $date, $device_code);
$stmt->execute();
$result = $stmt->get_result();

$first = $result->fetch_assoc();
$device_name = $first['device_name'] ?? $device_code;

header('Content-Type: application/gpx+xml');
header('Content-Disposition: attachment; filename="ride_' . $device_code . '_' . $date . '.gpx"');

echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
echo '<gpx version="1.1" creator="MotoTracker"' . "\n";
echo '     xmlns="http://www.topografix.com/GPX/1/1"' . "\n";
echo '     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' . "\n";
echo '     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">' . "\n";
echo '  <trk>' . "\n";
echo '    <name>' . htmlspecialchars($device_name) . ' ride ' . htmlspecialchars($date) . '</name>' . "\n";
echo '    <trkseg>' . "\n";

if ($first) {
    $time = date('Y-m-d\TH:i:s\Z', strtotime($first['timestamp']));
    echo '      <trkpt lat="' . $first['lat'] . '" lon="' . $first['lon'] . '">' . "\n";
    echo '        <time>' . $time . '</time>' . "\n";
    echo '      </trkpt>' . "\n";
}
while ($row = $result->fetch_assoc()) {
    $time = date('Y-m-d\TH:i:s\Z', strtotime($row['timestamp']));
    echo '      <trkpt lat="' . $row['lat'] . '" lon="' . $row['lon'] . '">' . "\n";
    echo '        <time>' . $time . '</time>' . "\n";
    echo '      </trkpt>' . "\n";
}

echo '    </trkseg>' . "\n";
echo '  </trk>' . "\n";
echo '</gpx>' . "\n";

$stmt->close();
$auth_conn->close();
