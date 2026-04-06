function haversineDistance($lat1, $lon1, $lat2, $lon2) {
    $earthRadius = 6371; // Promień Ziemi w kilometrach

    $dLat = deg2rad($lat2 - $lat1);
    $dLon = deg2rad($lon2 - $lon1);

    $a = sin($dLat / 2) * sin($dLat / 2) +
         cos(deg2rad($lat1)) * cos(deg2rad($lat2)) *
         sin($dLon / 2) * sin($dLon / 2);

    $c = 2 * atan2(sqrt($a), sqrt(1 - $a));

    $distance = $earthRadius * $c;

    return $distance;
}

// Przykładowe użycie
$latitude1 = 52.5200; // Latitude punktu 1
$longitude1 = 13.4050; // Longitude punktu 1

$latitude2 = 48.8566; // Latitude punktu 2
$longitude2 = 2.3522; // Longitude punktu 2

$distance = haversineDistance($latitude1, $longitude1, $latitude2, $longitude2);
echo "Odległość między punktami: " . $distance . " km";
