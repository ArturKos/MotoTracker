<?php
include('gps_track_config.php');

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);

if ($conn->connect_error) {
    die("Connection failed");
}

$date = isset($_GET['date']) && $_GET['date'] !== '' ? $_GET['date'] : date('Y-m-d');

$stmt = $conn->prepare("SELECT lat, lon, timestamp, temperature FROM S_02 WHERE DATE(timestamp) = ? ORDER BY timestamp ASC");
$stmt->bind_param("s", $date);
$stmt->execute();
$result = $stmt->get_result();

header('Content-Type: application/gpx+xml');
header('Content-Disposition: attachment; filename="ride_' . $date . '.gpx"');

echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
echo '<gpx version="1.1" creator="MotoTracker"' . "\n";
echo '     xmlns="http://www.topografix.com/GPX/1/1"' . "\n";
echo '     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' . "\n";
echo '     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">' . "\n";
echo '  <trk>' . "\n";
echo '    <name>Motorcycle Ride ' . htmlspecialchars($date) . '</name>' . "\n";
echo '    <trkseg>' . "\n";

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
$conn->close();
?>
