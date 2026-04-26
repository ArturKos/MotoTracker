<?php
include('gps_track_config.php');
include('auth.php');

$date = isset($_GET['date']) && $_GET['date'] !== '' ? $_GET['date'] : date('Y-m-d');
$device_code = $_GET['device'] ?? 'all';
$user_filter = device_user_filter();

if ($device_code === 'all') {
    $sql = "SELECT d.code AS device_code, d.name AS device_name, d.color AS device_color,
                   p.lat, p.lon, p.speed, p.timestamp, p.temperature, p.humidyty, p.battery
            FROM points p
            JOIN devices d ON p.device_id = d.id
            WHERE DATE(p.timestamp) = ? AND d.active = 1 $user_filter
            ORDER BY d.id, p.timestamp ASC";
    $stmt = $auth_conn->prepare($sql);
    $stmt->bind_param("s", $date);
    $stmt->execute();
    $result = $stmt->get_result();

    $devices = [];
    while ($row = $result->fetch_assoc()) {
        $code = $row['device_code'];
        if (!isset($devices[$code])) {
            $devices[$code] = [
                'code'   => $code,
                'name'   => $row['device_name'],
                'color'  => $row['device_color'],
                'points' => [],
            ];
        }
        $devices[$code]['points'][] = [
            'lat'         => (float)$row['lat'],
            'lon'         => (float)$row['lon'],
            'speed'       => $row['speed']       !== null ? (float)$row['speed']       : null,
            'timestamp'   => $row['timestamp'],
            'temperature' => $row['temperature'] !== null ? (float)$row['temperature'] : null,
            'humidyty'    => $row['humidyty']    !== null ? (float)$row['humidyty']    : null,
            'battery'     => $row['battery']     !== null ? (float)$row['battery']     : null,
        ];
    }
    header('Content-Type: application/json');
    echo json_encode(array_values($devices));
    exit;
}

$sql = "SELECT p.lat, p.lon, p.speed, p.timestamp, p.temperature, p.humidyty, p.battery
        FROM points p
        JOIN devices d ON p.device_id = d.id
        WHERE DATE(p.timestamp) = ? AND d.code = ? AND d.active = 1 $user_filter
        ORDER BY p.timestamp ASC";
$stmt = $auth_conn->prepare($sql);
$stmt->bind_param("ss", $date, $device_code);
$stmt->execute();
$result = $stmt->get_result();

$points = [];
while ($row = $result->fetch_assoc()) {
    $row['lat']         = (float)$row['lat'];
    $row['lon']         = (float)$row['lon'];
    $row['speed']       = $row['speed']       !== null ? (float)$row['speed']       : null;
    $row['temperature'] = $row['temperature'] !== null ? (float)$row['temperature'] : null;
    $row['humidyty']    = $row['humidyty']    !== null ? (float)$row['humidyty']    : null;
    $row['battery']     = $row['battery']     !== null ? (float)$row['battery']     : null;
    $points[] = $row;
}

header('Content-Type: application/json');
echo json_encode($points);
