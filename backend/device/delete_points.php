<?php
// Delete points for a device in a time range. Cascades: interpolated rows
// whose parent_id points at any deleted raw row also go (set to NULL would
// orphan them and they're just road-sampling artifacts, not data).
//
// Params:
//   device_id : int (required, must be owned or caller must be admin)
//   from      : 'YYYY-MM-DD HH:MM:SS' or 'YYYY-MM-DD' (inclusive)
//   to        : same (exclusive end; end-of-day if date-only)
//   filter    : 'all' (default) | 'stationary' — stationary matches
//               speed IS NULL OR speed < 1 (km/h) — useful for cleaning
//               indoor GPS jitter clusters.
include('../gps_track_config.php');
include('../auth.php');
include('_common.php');

auth_require_write();

dev_require_post();
header('Content-Type: application/json');

$device_id = (int)($_POST['device_id'] ?? 0);
$from_raw  = trim((string)($_POST['from'] ?? ''));
$to_raw    = trim((string)($_POST['to']   ?? ''));
$filter    = strtolower(trim((string)($_POST['filter'] ?? 'all')));

if ($device_id <= 0) dev_bad_request('missing_device_id');
if ($from_raw === '' || $to_raw === '') dev_bad_request('missing_range');
if (!in_array($filter, ['all', 'stationary'], true)) dev_bad_request('invalid_filter');

function normalize_ts($raw, $end_of_day = false) {
    // Accept 'YYYY-MM-DD' or 'YYYY-MM-DD HH:MM[:SS]'.
    if (preg_match('/^\d{4}-\d{2}-\d{2}$/', $raw)) {
        return $raw . ($end_of_day ? ' 23:59:59' : ' 00:00:00');
    }
    if (preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}(:\d{2})?$/', $raw)) {
        return strlen($raw) === 16 ? $raw . ':00' : $raw;
    }
    return null;
}

$from = normalize_ts($from_raw, false);
$to   = normalize_ts($to_raw,   true);
if ($from === null || $to === null) dev_bad_request('invalid_range_format');
if (strcmp($from, $to) > 0) dev_bad_request('from_after_to');

$dev = dev_load_owned($auth_conn, $device_id);

// Delete children of matching raw rows first (they'd be orphans after).
// A child is "interpolated" with parent_id in the to-be-deleted set.
$auth_conn->begin_transaction();

try {
    if ($filter === 'stationary') {
        $speed_clause = " AND (speed IS NULL OR speed < 1)";
    } else {
        $speed_clause = "";
    }

    // Cascade first.
    $sql_child = "DELETE c FROM points c
                  JOIN points p ON p.id = c.parent_id
                  WHERE p.device_id = ?
                    AND p.timestamp >= ?
                    AND p.timestamp <= ?
                    $speed_clause";
    $stmt = $auth_conn->prepare($sql_child);
    $stmt->bind_param("iss", $device_id, $from, $to);
    $stmt->execute();
    $cascaded = $stmt->affected_rows;
    $stmt->close();

    $sql_main = "DELETE FROM points
                 WHERE device_id = ?
                   AND timestamp >= ?
                   AND timestamp <= ?
                   $speed_clause";
    $stmt = $auth_conn->prepare($sql_main);
    $stmt->bind_param("iss", $device_id, $from, $to);
    $stmt->execute();
    $deleted = $stmt->affected_rows;
    $stmt->close();

    $auth_conn->commit();
} catch (Throwable $e) {
    $auth_conn->rollback();
    http_response_code(500);
    echo json_encode(['error' => 'delete_failed']);
    exit;
}

echo json_encode([
    'ok' => true,
    'device_id' => $device_id,
    'deleted' => $deleted,
    'cascaded' => $cascaded,
    'from' => $from,
    'to' => $to,
    'filter' => $filter,
]);
$auth_conn->close();
