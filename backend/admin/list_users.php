<?php
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

header('Content-Type: application/json');

$sql = "
    SELECT
        u.id,
        u.username,
        u.display_name,
        u.is_admin,
        u.active,
        u.must_change_password,
        u.read_api_key,
        u.created_at,
        (SELECT COUNT(*) FROM devices d WHERE d.user_id = u.id)                        AS device_count,
        (SELECT MAX(p.timestamp) FROM devices d
            JOIN points p ON p.device_id = d.id
            WHERE d.user_id = u.id)                                                    AS last_point_at,
        (SELECT COUNT(*) FROM devices d
            JOIN points p ON p.device_id = d.id
            WHERE d.user_id = u.id
              AND p.timestamp >= NOW() - INTERVAL 7 DAY)                               AS points_7d
    FROM users u
    ORDER BY u.id
";

$result = $auth_conn->query($sql);
$rows = [];
while ($r = $result->fetch_assoc()) {
    $r['id']                   = (int)$r['id'];
    $r['is_admin']             = (int)$r['is_admin'];
    $r['active']               = (int)$r['active'];
    $r['must_change_password'] = (int)$r['must_change_password'];
    $r['device_count']         = (int)$r['device_count'];
    $r['points_7d']            = (int)$r['points_7d'];
    $rows[] = $r;
}

echo json_encode($rows);
$auth_conn->close();
