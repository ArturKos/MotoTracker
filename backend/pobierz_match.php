<?php
include('gps_track_config.php');
include('auth.php');

// OSRM Map Matching endpoint — returns road-matched polyline geometry.
// Uses the OSRM Poland addon on Home Assistant (192.168.1.142:5001).
// Front-end fetches this in parallel with pobierz_punkty.php and uses it
// to render the track polyline; markers/stats still come from raw points.

const OSRM_BASE     = 'http://192.168.1.142:5001';
const OSRM_CHUNK    = 800;   // addon max_matching_size=1000, leave headroom
const OSRM_TIMEOUT  = 20;

$date        = isset($_GET['date']) && $_GET['date'] !== '' ? $_GET['date'] : date('Y-m-d');
$device_code = $_GET['device'] ?? '';

if ($device_code === '' || $device_code === 'all') {
    http_response_code(400);
    header('Content-Type: application/json');
    echo json_encode(['error' => 'device required']);
    exit;
}

$user_filter = device_user_filter();

$sql = "SELECT p.lat, p.lon, p.timestamp
        FROM points p
        JOIN devices d ON p.device_id = d.id
        WHERE DATE(p.timestamp) = ? AND d.code = ? AND d.active = 1
              AND p.source IN ('raw','snapped')
              $user_filter
        ORDER BY p.timestamp ASC";
$stmt = $auth_conn->prepare($sql);
$stmt->bind_param("ss", $date, $device_code);
$stmt->execute();
$result = $stmt->get_result();

$points = [];
while ($row = $result->fetch_assoc()) {
    $points[] = [
        'lat' => (float)$row['lat'],
        'lon' => (float)$row['lon'],
        'ts'  => strtotime($row['timestamp']),
    ];
}

function osrm_match(array $chunk) {
    $coord_parts = [];
    $ts_parts    = [];
    foreach ($chunk as $p) {
        $coord_parts[] = number_format($p['lon'], 6, '.', '') . ',' . number_format($p['lat'], 6, '.', '');
        $ts_parts[]    = (string)$p['ts'];
    }
    $url = OSRM_BASE . '/match/v1/driving/' . implode(';', $coord_parts)
         . '?geometries=geojson&overview=full&tidy=true&timestamps=' . implode(';', $ts_parts);

    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => OSRM_TIMEOUT,
    ]);
    $body = curl_exec($ch);
    $http = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($http !== 200 || !$body) return null;
    $data = json_decode($body, true);
    if (!isset($data['matchings']) || empty($data['matchings'])) return null;

    $coords = [];
    foreach ($data['matchings'] as $m) {
        if (!isset($m['geometry']['coordinates'])) continue;
        foreach ($m['geometry']['coordinates'] as $c) $coords[] = $c;
    }
    return $coords;
}

$response = [
    'matched_geometry' => null,
    'stats' => [
        'input_points'   => count($points),
        'chunks_total'   => 0,
        'chunks_failed'  => 0,
        'output_coords'  => 0,
    ],
];

if (count($points) >= 2) {
    $merged = [];
    $n      = count($points);
    $start  = 0;
    while ($start < $n - 1) {
        $end   = min($start + OSRM_CHUNK, $n);
        $chunk = array_slice($points, $start, $end - $start);
        $coords = osrm_match($chunk);
        $response['stats']['chunks_total']++;
        if ($coords === null) {
            $response['stats']['chunks_failed']++;
        } else {
            // Drop leading coord of subsequent chunks to avoid duplicate at the 1-point overlap
            if (!empty($merged)) array_shift($coords);
            foreach ($coords as $c) $merged[] = $c;
        }
        if ($end >= $n) break;
        $start = $end - 1; // 1-point overlap to keep continuity
    }
    if (!empty($merged)) {
        $response['matched_geometry'] = [
            'type'        => 'LineString',
            'coordinates' => $merged,
        ];
        $response['stats']['output_coords'] = count($merged);
    }
}

header('Content-Type: application/json');
echo json_encode($response);
