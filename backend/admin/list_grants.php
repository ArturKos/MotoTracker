<?php
// GET ?grantee=<user_id> — list the view_grants for one account, with a
// human label for each granted resource (device name/code or app-user e-mail).
include('../gps_track_config.php');
include('../auth.php');
include('_gate.php');

header('Content-Type: application/json');

$grantee = (int)($_GET['grantee'] ?? 0);
if ($grantee <= 0) admin_bad_request('missing_grantee');

$stmt = $auth_conn->prepare(
    "SELECT vg.id, vg.resource_type, vg.resource_id, vg.created_at,
            d.code AS device_code, d.name AS device_name,
            u.username AS app_user_name, u.email AS app_user_email
     FROM view_grants vg
     LEFT JOIN devices d ON vg.resource_type = 'device'   AND d.id = vg.resource_id
     LEFT JOIN users   u ON vg.resource_type = 'app_user' AND u.id = vg.resource_id
     WHERE vg.grantee_user_id = ?
     ORDER BY vg.resource_type, vg.id"
);
$stmt->bind_param("i", $grantee);
$stmt->execute();
$res = $stmt->get_result();
$rows = [];
while ($r = $res->fetch_assoc()) {
    $label = $r['resource_type'] === 'device'
        ? trim(($r['device_name'] ?? '') . ' (' . ($r['device_code'] ?? '?') . ')')
        : ($r['app_user_email'] ?: $r['app_user_name'] ?: ('#' . $r['resource_id']));
    $rows[] = [
        'id'            => (int)$r['id'],
        'resource_type' => $r['resource_type'],
        'resource_id'   => (int)$r['resource_id'],
        'label'         => $label,
        'created_at'    => $r['created_at'],
    ];
}
$stmt->close();
echo json_encode($rows);
$auth_conn->close();
