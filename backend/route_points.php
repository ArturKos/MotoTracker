<?php
// Współdzielony parser ścieżki trasy (pathJson) na wiersze punktów dla tabeli `points`.
// Używany przez api_routes.php (upload z appki) i tools/backfill_rides_to_points.php.
//
// Wejście: pathJson jako string. Dwa formaty:
//   - obiektowy (aplikacja, N1+):  [{"lat":..,"lng":..,"ele":..,"t":<epoch_ms>}, ...]
//   - legacy pary:                 [[lat,lng], ...]
// Wyjście: [['lat'=>, 'lon'=>, 'ele'=>?, 'ts_ms'=>?, 'speed'=>?km/h], ...]
// Prędkość liczona z dystansu haversine / Δt między kolejnymi punktami z timestampem.

function rp_haversine_km($la1, $lo1, $la2, $lo2) {
    $R = 6371.0;
    $dLa = deg2rad($la2 - $la1);
    $dLo = deg2rad($lo2 - $lo1);
    $a = sin($dLa / 2) ** 2
       + cos(deg2rad($la1)) * cos(deg2rad($la2)) * sin($dLo / 2) ** 2;
    return $R * 2 * atan2(sqrt($a), sqrt(1 - $a));
}

function parse_path_points(?string $pathJson): array {
    if ($pathJson === null || $pathJson === '') return [];
    $arr = json_decode($pathJson, true);
    if (!is_array($arr)) return [];

    $pts = [];
    foreach ($arr as $e) {
        if (is_array($e) && array_key_exists('lat', $e)) {          // obiekt {lat,lng,...}
            $lat = (float)$e['lat'];
            $lon = (float)($e['lng'] ?? $e['lon'] ?? 0);
            $ele = array_key_exists('ele', $e) ? (float)$e['ele'] : null;
            // Milliseconds since epoch: keep as float, NEVER cast raw ms to int —
            // on 32-bit PHP (armv7l server) (int)1.7e12 overflows. Consumers divide
            // by 1000 first (seconds ~1.7e9 fit a 32-bit int until 2038) before (int).
            $ts  = array_key_exists('t', $e) && $e['t'] !== null ? (float)$e['t'] : null;
        } elseif (is_array($e) && isset($e[0], $e[1])) {            // legacy [lat,lng]
            $lat = (float)$e[0];
            $lon = (float)$e[1];
            $ele = null;
            $ts  = null;
        } else {
            continue;
        }
        $pts[] = ['lat' => $lat, 'lon' => $lon, 'ele' => $ele, 'ts_ms' => $ts, 'speed' => null];
    }

    // Derywacja prędkości z sąsiednich punktów mających timestamp.
    $n = count($pts);
    for ($i = 1; $i < $n; $i++) {
        $a = $pts[$i - 1];
        $b = $pts[$i];
        if ($a['ts_ms'] !== null && $b['ts_ms'] !== null && $b['ts_ms'] > $a['ts_ms']) {
            $km = rp_haversine_km($a['lat'], $a['lon'], $b['lat'], $b['lon']);
            $h  = ($b['ts_ms'] - $a['ts_ms']) / 3600000.0;
            $pts[$i]['speed'] = $h > 0 ? round($km / $h, 2) : null;
        }
    }
    return $pts;
}
