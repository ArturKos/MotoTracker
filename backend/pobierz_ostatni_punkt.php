<?php
include('gps_track_config.php');

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);

if ($conn->connect_error) {
    die(json_encode(['error' => 'Connection failed']));
}

$date = isset($_GET['date']) && $_GET['date'] !== '' ? $_GET['date'] : date('Y-m-d');

$stmt = $conn->prepare("SELECT lat, lon, timestamp, temperature, humidyty, battery FROM S_02 WHERE DATE(timestamp) = ? ORDER BY timestamp DESC LIMIT 1");
$stmt->bind_param("s", $date);
$stmt->execute();
$result = $stmt->get_result();

$points = array();
while ($row = $result->fetch_assoc()) {
    $row['lat'] = (float)$row['lat'];
    $row['lon'] = (float)$row['lon'];
    $row['temperature'] = (float)$row['temperature'];
    $row['humidyty'] = (float)$row['humidyty'];
    $row['battery'] = (float)$row['battery'];
    $points[] = $row;
}

header('Content-Type: application/json');
echo json_encode($points);

$stmt->close();
$conn->close();
?>
