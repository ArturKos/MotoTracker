<?php
include('gps_track_config.php');
include('auth.php');

// Admins see all devices when logged in interactively, but a read-only token
// is always scoped to its owner (so HA embeds don't expose other users' devices).
$where = ($current_user['is_admin'] && !$auth_readonly)
    ? ""
    : "AND user_id = " . $current_user['id'];
$sql = "SELECT id, code, name, color, active FROM devices WHERE active = 1 $where ORDER BY id";
$result = $auth_conn->query($sql);

$devices = [];
while ($row = $result->fetch_assoc()) {
    $row['id']     = (int)$row['id'];
    $row['active'] = (int)$row['active'];
    $devices[] = $row;
}

header('Content-Type: application/json');
echo json_encode($devices);
$auth_conn->close();
