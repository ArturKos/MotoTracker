<?php
// Create a MotoTracker user + their first device in one atomic step.
// Prints the user's fresh write_api_key at the end — share it so they can
// push GPS data for their device.
//
// Usage:   php tools/add_user.php <username> <password> <device_code> <device_name> [--color=#hex] [--admin]
// Example: php tools/add_user.php alice hunter2 ALICE_BIKE "Alice's Bike"
//          php tools/add_user.php bob   s3cret  BOB_CAR    "Bob's Car"    --color=#9b59b6
//          php tools/add_user.php ops   s3cret  OPS_TEST   "Ops Test"     --admin

if (PHP_SAPI !== 'cli') {
    fwrite(STDERR, "CLI only.\n"); exit(1);
}
if ($argc < 5) {
    fwrite(STDERR, "Usage: php add_user.php <username> <password> <device_code> <device_name> [--color=#hex] [--admin]\n");
    exit(1);
}
$username    = trim($argv[1]);
$password    = $argv[2];
$device_code = trim($argv[3]);
$device_name = trim($argv[4]);

$flags = array_slice($argv, 5);
$is_admin = in_array('--admin', $flags, true) ? 1 : 0;
$color = '#3498db';
foreach ($flags as $f) {
    if (strpos($f, '--color=') === 0) {
        $color = substr($f, 8);
    }
}
if (!preg_match('/^#[0-9a-fA-F]{6}$/', $color)) {
    fwrite(STDERR, "Invalid --color (expected #rrggbb): $color\n");
    exit(1);
}

require __DIR__ . '/../gps_track_config.php';

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) {
    fwrite(STDERR, "DB connect error: " . $conn->connect_error . "\n");
    exit(1);
}

$conn->begin_transaction();

try {
    $hash = password_hash($password, PASSWORD_BCRYPT);
    $api_key = bin2hex(random_bytes(16));
    $must_change = 0;

    $stmt = $conn->prepare(
        "INSERT INTO users (username, password_hash, display_name, write_api_key, is_admin, must_change_password)
         VALUES (?, ?, ?, ?, ?, ?)"
    );
    $stmt->bind_param("ssssii", $username, $hash, $username, $api_key, $is_admin, $must_change);
    if (!$stmt->execute()) {
        throw new RuntimeException("Failed to create user: " . $stmt->error);
    }
    $user_id = $stmt->insert_id;
    $stmt->close();

    $stmt = $conn->prepare(
        "INSERT INTO devices (code, name, color, active, user_id) VALUES (?, ?, ?, 1, ?)"
    );
    $stmt->bind_param("sssi", $device_code, $device_name, $color, $user_id);
    if (!$stmt->execute()) {
        throw new RuntimeException("Failed to create device: " . $stmt->error);
    }
    $device_id = $stmt->insert_id;
    $stmt->close();

    $conn->commit();
} catch (Throwable $e) {
    $conn->rollback();
    fwrite(STDERR, $e->getMessage() . "\n");
    exit(1);
}

echo "User created:   id=$user_id username=$username is_admin=$is_admin\n";
echo "Device created: id=$device_id code=$device_code name=\"$device_name\" color=$color\n";
echo "Write API key:  $api_key\n";
echo "Share the API key + device code with the user so their tracker can push data.\n";
