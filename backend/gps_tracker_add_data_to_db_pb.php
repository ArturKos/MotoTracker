<?php
//artur-kos.tplinkdns.com/gpstrack/gps_tracker_add_data_to_db.php?api_key=XmzxW5D1Gx5gvYHp17NaJKGSUQ9H8VJu&vehicle_id=S_02&lat=53.426102114318134&lon=14.544657069340884
include('gps_track_config.php');
require 'vendor/autoload.php';

use example\SensorData;

// Odbierz dane z ciała żądania
$data = file_get_contents("php://input");

try {
    // Deserializuj dane Protocol Buffers
    $sensorData = new SensorData();
    $sensorData->mergeFromString($data);

    // Wyświetl odebrane dane
    echo "Received Sensor ID: " . $sensorData->getSensorId() . PHP_EOL;
    echo "Received Sensor Value: " . $sensorData->getSensorValue() . PHP_EOL;

    // Tutaj możesz przetworzyć dane dalej, zapisywać do bazy danych, itp.

    echo "Data processed successfully";
} catch (Exception $e) {
    echo "Error processing data: " . $e->getMessage();
}


$w_api_key = $_GET['api_key'];
$table = $_GET['vehicle_id'];
$lat = $_GET['lat'];
$lon = $_GET['lon'];
$temperature =$_GET['temperature'];
$humidyty =$_GET['humidyty'];
$battery =$_GET['battery'];

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);

if ($w_api_key == $gps_db_write_api_key ) {

$date = date('Y-m-d H:i:s', time());

	$result = mysqli_query($conn, "INSERT INTO $table(lat, lon, temperature, humidyty, battery) VALUES ($lat, $lon,$temperature,$humidyty,$battery)");
	if ($result === false){
		echo "ERR";
		$conn -> close();
	}
	else {
		echo "OK";
		$conn -> close();
	}
} else {
echo "Niepoprawny API-KEY";
}
?>
