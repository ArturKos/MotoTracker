<?php
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

header('Content-Type: application/json');

// Full device list with owner info — used by the admin reassign modal.
$sql = "
    SELECT
        d.id, d.code, d.name, d.color, d.active, d.user_id,
        u.username AS owner_username,
        (SELECT MAX(p.timestamp) FROM points p WHERE p.device_id = d.id) AS last_point_at
    FROM devices d
    LEFT JOIN users u ON u.id = d.user_id
    ORDER BY d.id
";
$result = $auth_conn->query($sql);
$rows = [];
while ($r = $result->fetch_assoc()) {
    $r['id']      = (int)$r['id'];
    $r['active']  = (int)$r['active'];
    $r['user_id'] = $r['user_id'] === null ? null : (int)$r['user_id'];
    $rows[] = $r;
}

echo json_encode($rows);
$auth_conn->close();
