<?php
include('gps_track_config.php');

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);

if ($conn->connect_error) {
    die(json_encode(['error' => 'Connection failed']));
}

$stmt = $conn->prepare("SELECT DATE(timestamp) as ride_date, COUNT(*) as points, MIN(timestamp) as start_time, MAX(timestamp) as end_time FROM S_02 GROUP BY DATE(timestamp) ORDER BY ride_date DESC LIMIT 50");
$stmt->execute();
$result = $stmt->get_result();

$rides = array();
while ($row = $result->fetch_assoc()) {
    $rides[] = $row;
}

header('Content-Type: application/json');
echo json_encode($rides);

$stmt->close();
$conn->close();
?>
