# MotoTracker multi-user — post-deploy runbook

State on `malinka:/var/www/html/gpstrack/` after the 2026-04-19 rollout.

## First-run admin setup

1. Open http://192.168.1.145/gpstrack/login.html
2. Log in as `artur / changeme`
3. You will be redirected to `change_password.html` (admin has `must_change_password=1`) — set a real password.

Admin id=1 owns all 7 existing devices (S_02, HA_1..HA_6). Admin's `write_api_key` is the original
`$gps_db_write_api_key` from `gps_track_config.php`, so HA push and LilyGO firmware keep working
unchanged.

## Add a friend (user + first device in one step)

```bash
ssh malinka
cd /var/www/html/gpstrack
sudo -u www-data php tools/add_user.php <username> <password> <device_code> <device_name> [--color=#rrggbb] [--admin]
```

Example:

```bash
sudo -u www-data php tools/add_user.php alice hunter2 ALICE_BIKE "Alice's Bike" --color=#9b59b6
```

The script runs user + device inserts inside a single transaction (rollback on failure) and
prints the generated write API key. Hand the API key + device code to the friend so their
firmware / phone client can push GPS data.

## Give an existing friend another device

```sql
-- mysql -u gps_db_dbuser -p gps_db_data
SELECT id, username FROM users;
INSERT INTO devices (code, name, color, active, user_id)
VALUES ('ALICE_CAR', 'Alice Car', '#2ecc71', 1, <alice_id>);
```

## Hand over an existing admin-owned device

```sql
UPDATE devices SET user_id = <friend_id> WHERE code = '<DEVICE_CODE>';
```

Friend now sees only their own devices in the map UI. Admin still sees everything.

Alternatively, do it from the admin panel (`admin.html` → Devices → Reassign)
— same effect, no SQL needed.

## Self-service device management

Each owner gets a cog icon in the top bar that opens a two-tab modal:

- **Info**: rename the device and change the polyline colour. Takes effect
  on the next map load.
- **Cleanup**: delete points in a date range, either everything or only
  stationary jitter (`speed < 1 km/h`). Interpolated children cascade via
  `parent_id`, and the whole op is transactional — a partial failure rolls
  back. Admins see the cog on every device and can clean up anyone's data.

Endpoints: `POST device/update.php`, `POST device/delete_points.php`.
Both enforce ownership via `device/_common.php::dev_load_owned()`.

## Write-ingest authorization rule

`gps_tracker_add_data_to_db.php` enforces:

1. `k=<api_key>` must match a row in `users.write_api_key`.
2. The `devices.user_id` of the target device code (`v=<code>`) must equal the user id,
   unless that user has `is_admin=1`.

So leaking a friend's API key cannot be used to write into other users' devices.

## Frontend cache-busting

`index.html` currently loads `style.css?v=5` and `map.js?v=5`. Bump the suffix on any UI change
so browsers don't serve stale bundles.

## Rollback

- Revert backend files from git.
- `ALTER TABLE devices DROP FOREIGN KEY fk_devices_user; ALTER TABLE devices DROP COLUMN user_id; DROP TABLE users;`
- HA push and firmware keep working because ingest falls back to the legacy key check once
  `auth.php` / per-user lookup are reverted.
