<?php
include('gps_track_config.php');
include('auth.php');

$device_code = $_GET['device'] ?? 'all';
$user_filter = device_user_filter();

if ($device_code === 'all') {
    $sql = "SELECT d.code AS device_code, d.name AS device_name, d.color AS device_color,
                   DATE(p.timestamp) AS ride_date,
                   COUNT(*) AS points,
                   MIN(p.timestamp) AS start_time,
                   MAX(p.timestamp) AS end_time
            FROM points p
            JOIN devices d ON p.device_id = d.id
            WHERE d.active = 1 $user_filter
            GROUP BY d.id, DATE(p.timestamp)
            ORDER BY ride_date DESC, d.id
            LIMIT 200";
    $stmt = $auth_conn->prepare($sql);
} else {
    $sql = "SELECT d.code AS device_code, d.name AS device_name, d.color AS device_color,
                   DATE(p.timestamp) AS ride_date,
                   COUNT(*) AS points,
                   MIN(p.timestamp) AS start_time,
                   MAX(p.timestamp) AS end_time
            FROM points p
            JOIN devices d ON p.device_id = d.id
            WHERE d.code = ? AND d.active = 1 $user_filter
            GROUP BY d.id, DATE(p.timestamp)
            ORDER BY ride_date DESC
            LIMIT 200";
    $stmt = $auth_conn->prepare($sql);
    $stmt->bind_param("s", $device_code);
}
$stmt->execute();
$result = $stmt->get_result();

$rides = [];
while ($row = $result->fetch_assoc()) {
    $rides[] = $row;
}

header('Content-Type: application/json');
echo json_encode($rides);
