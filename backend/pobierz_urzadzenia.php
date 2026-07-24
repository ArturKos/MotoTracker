<?php
include('gps_track_config.php');
include('auth.php');

// Admins see all devices when logged in interactively, but a read-only token
// is always scoped to its owner (so HA embeds don't expose other users' devices).
// Non-admins additionally see devices shared via view_grants (resource_type='device'),
// so a granted account finds the shared device (incl. a phone) in the list — consistent
// with device_user_filter() used by pobierz_punkty.php for the points themselves.
if ($current_user['is_admin'] && !$auth_readonly) {
    $where = "";
} else {
    $uid = (int)$current_user['id'];
    $granted = grant_resource_ids('device'); // ints from view_grants, safe to splice
    $where = empty($granted)
        ? "AND user_id = $uid"
        : "AND (user_id = $uid OR id IN (" . implode(',', $granted) . "))";
}
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
