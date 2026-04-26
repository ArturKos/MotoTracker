<?php
// One-time admin seeder. Re-runnable: it detects an existing admin and
// exits. Uses the legacy $gps_db_write_api_key as the admin's write key
// so HA push and firmware ingest keep working across the migration.
//
// Usage:   php tools/bootstrap_admin.php <username> <password>
// Example: php tools/bootstrap_admin.php artur changeme

if (PHP_SAPI !== 'cli') {
    fwrite(STDERR, "This script must be run from the command line.\n");
    exit(1);
}
if ($argc < 3) {
    fwrite(STDERR, "Usage: php bootstrap_admin.php <username> <password>\n");
    exit(1);
}
$username = trim($argv[1]);
$password = $argv[2];

require __DIR__ . '/../gps_track_config.php';

$conn = mysqli_connect($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) {
    fwrite(STDERR, "DB connect error: " . $conn->connect_error . "\n");
    exit(1);
}

// If any admin already exists, skip seeding.
$res = $conn->query("SELECT id, username FROM users WHERE is_admin = 1 LIMIT 1");
if ($res && ($existing = $res->fetch_assoc())) {
    echo "Admin already exists: id={$existing['id']} username={$existing['username']}\n";
    echo "Nothing to do.\n";
    exit(0);
}

$hash = password_hash($password, PASSWORD_BCRYPT);
// Preserve the legacy write_api_key so HA push and firmware keep working.
$api_key = $gps_db_write_api_key ?? bin2hex(random_bytes(16));
$must_change = 1;

$stmt = $conn->prepare(
    "INSERT INTO users (username, password_hash, display_name, write_api_key, is_admin, must_change_password)
     VALUES (?, ?, ?, ?, 1, ?)"
);
$display = $username;
$stmt->bind_param("ssssi", $username, $hash, $display, $api_key, $must_change);
$stmt->execute();
$admin_id = $stmt->insert_id;
$stmt->close();

// Claim every existing device for the admin.
$conn->query("UPDATE devices SET user_id = $admin_id WHERE user_id IS NULL");

// Make user_id NOT NULL and add the FK (safe now that every row has a user).
$conn->query("ALTER TABLE devices MODIFY COLUMN user_id INT NOT NULL");
// Ignore FK duplicate errors on re-runs.
@$conn->query("ALTER TABLE devices ADD CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id)");

echo "Admin created: id=$admin_id username=$username\n";
echo "Write API key (keep this secret): $api_key\n";
echo "You will be asked to change the password on first login.\n";
