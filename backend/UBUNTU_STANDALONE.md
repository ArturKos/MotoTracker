# MotoTracker — standalone instance on ubuntu

Second MotoTracker deployment, running side-by-side with malinka. This one has
**no Home Assistant / Traccar integration** — friends' phones POST directly to
the backend. Malinka remains the personal-tracker instance with the HA/OSRM
pipeline and is not touched by this setup.

Installed 2026-04-19 on `ssh ubuntu` (messiahubuntuserver, 192.168.1.135).

## Host snapshot

| | |
|---|---|
| Hostname | messiahubuntuserver |
| IP | 192.168.1.135 |
| OS | Ubuntu 24.04 LTS (x86_64) |
| CPU / RAM / Disk | 2 CPU / 7.2 GiB / 430 GB free on `/` |
| HTTP | Apache 2.4 on :80 (nginx also runs on :8000, unrelated) |
| PHP | 8.3 with `libapache2-mod-php`, `php-mysql`, `php-curl`, `php-mbstring`, `php-gd`, `php-xml` |
| DB | mariadb-server (local, default config) |
| Docker | installed (no MotoTracker containers yet; reserved for OSRM stage 2) |

## Apache layout

Apache's default vhost still serves `/var/www/upload` as an auto-index (the
pre-existing pcap/file share). MotoTracker is added via a conf snippet, **not**
a new vhost, so the two paths coexist on port 80:

- `http://192.168.1.135/` → existing upload index
- `http://192.168.1.135/gpstrack/` → MotoTracker

Config: `/etc/apache2/conf-available/mototracker.conf`

```apache
Alias /gpstrack /var/www/html/gpstrack
<Directory /var/www/html/gpstrack>
    Options -Indexes +FollowSymLinks
    AllowOverride None
    Require all granted
    DirectoryIndex index.html index.php
    <FilesMatch "\.php$">
        SetHandler application/x-httpd-php
    </FilesMatch>
    <FilesMatch "(gps_track_config\.php|\.sql|\.md|^log_file\.log)$">
        Require all denied
    </FilesMatch>
</Directory>
<Directory /var/www/html/gpstrack/migrations>
    Require all denied
</Directory>
<Directory /var/www/html/gpstrack/tools>
    Require all denied
</Directory>
```

Enable / reload:

```bash
sudo a2enconf mototracker
sudo apache2ctl configtest
sudo systemctl reload apache2
```

## Database

- Database: `gps_db_data`
- User: `gps_db_dbuser@localhost`
- Password: stored in `/var/www/html/gpstrack/gps_track_config.php` (mode 640, owner `www-data`). **Not** the same password as malinka — ubuntu is a fully separate DB.
- Write API key (admin): stored as `$gps_db_write_api_key` in the same file, and reused by `bootstrap_admin.php` so admin's `users.write_api_key` matches.

Direct DB shell:

```bash
sudo cat /var/www/html/gpstrack/gps_track_config.php    # to read the creds
mysql -u gps_db_dbuser -p gps_db_data
```

## Initial bootstrap (reproduce from scratch)

```bash
ssh ubuntu

# 1. Stack
sudo apt-get update
sudo apt-get install -y php libapache2-mod-php php-mysql php-curl \
                        php-mbstring php-gd php-xml composer mariadb-server

# 2. DB
sudo mariadb <<'SQL'
CREATE DATABASE IF NOT EXISTS gps_db_data
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'gps_db_dbuser'@'localhost'
    IDENTIFIED BY '<RANDOM-32-CHAR-PASSWORD>';
GRANT ALL PRIVILEGES ON gps_db_data.* TO 'gps_db_dbuser'@'localhost';
FLUSH PRIVILEGES;
SQL

# 3. Apache conf (paste the snippet above into
#    /etc/apache2/conf-available/mototracker.conf)
sudo a2enconf mototracker && sudo systemctl reload apache2

# 4. Backend files (rsync from a git checkout)
sudo mkdir -p /var/www/html/gpstrack
rsync -az --exclude log_file.log --exclude vendor \
    <repo>/backend/ ubuntu:/tmp/mt_backend/
sudo cp -r /tmp/mt_backend/. /var/www/html/gpstrack/
sudo chown -R www-data:www-data /var/www/html/gpstrack

# 5. gps_track_config.php (DB creds + a fresh 32-hex write_api_key)
sudo nano /var/www/html/gpstrack/gps_track_config.php
sudo chmod 640 /var/www/html/gpstrack/gps_track_config.php
sudo chown www-data:www-data /var/www/html/gpstrack/gps_track_config.php

# 6. Migrations (run all in order — fresh install, so 001's S_02 legacy
#    migration is a no-op on this box)
cd /var/www/html/gpstrack
for f in migrations/*.sql; do
    mysql -u gps_db_dbuser -p gps_db_data < "$f"
done

# 7. Since this is a standalone friends instance, delete the 7 legacy
#    devices (S_02 + HA_*) that migration 001 seeds:
mysql -u gps_db_dbuser -p gps_db_data -e 'DELETE FROM devices;'

# 8. Admin user (reuses $gps_db_write_api_key from step 5)
sudo -u www-data php tools/bootstrap_admin.php artur <initial-password>
```

Browse to http://192.168.1.135/gpstrack/login.html and log in as `artur` — you
will be redirected to change the password on first login.

## Adding a friend

Same atomic `add_user.php` tool as malinka — creates the user + their first
device in a single DB transaction, prints the generated write API key:

```bash
cd /var/www/html/gpstrack
sudo -u www-data php tools/add_user.php \
    <username> <password> <DEVICE_CODE> "<Device Name>" [--color=#rrggbb]
```

Example:

```bash
sudo -u www-data php tools/add_user.php alice hunter2 ALICE_PHONE "Alice Phone" --color=#9b59b6
# User created:   id=2 username=alice is_admin=0
# Device created: id=8 code=ALICE_PHONE name="Alice Phone" color=#9b59b6
# Write API key:  <share this with Alice>
```

Give the friend: (a) their username + password for logging into the web UI,
(b) the **DEVICE_CODE** (e.g. `ALICE_PHONE`), and (c) the **write API key**.

## Phone configuration (Traccar Client / OsmAnd)

Friends' phones push positions to the standalone backend via the Traccar
Client / OsmAnd compatibility shim `ingest_traccar.php`. No custom app needed.

### Traccar Client (Android, iOS) — speed in knots

Server URL:

```
http://192.168.1.135/gpstrack/ingest_traccar.php?k=<WRITE_API_KEY>
```

Device identifier (in Traccar Client settings): set it to the MotoTracker
device code you handed out (e.g. `ALICE_PHONE`). The client appends
`&id=<identifier>&lat=…&lon=…&timestamp=<epoch>&speed=<knots>&batt=<percent>`.

### OsmAnd online tracking — speed in m/s

Append `&source=osmand` so the shim converts m/s → km/h instead of knots → km/h:

```
http://192.168.1.135/gpstrack/ingest_traccar.php?k=<WRITE_API_KEY>&source=osmand
```

### Shim authorization rules

- `k` must match a row in `users.write_api_key`.
- The `id` (device code) must match an existing `devices.code` whose `user_id`
  equals the API key's owner (admins bypass this check).
- Wrong key → `401 Invalid API key`. Wrong device → `404 Unknown device code`.
  Not your device → `403 Device not owned by this API key`.

### Firmware-style ingest (LilyGO-compatible)

The original short-param endpoint also works on this instance for any client
that already speaks the legacy protocol:

```
GET /gpstrack/gps_tracker_add_data_to_db.php?k=<KEY>&v=<CODE>&la=<LAT>&lo=<LON>&s=<KMH>&t=<TEMP>&h=<HUM>&b=<BAT>&ts=<YYYY-MM-DD%20HH:MM:SS>
```

## OSRM stage-2 (deferred)

The road-snap + route-interpolation pipeline exists as shared code in
`integrations/malinka/snap_interpolate.py` and migration
`003_point_source.sql` is applied here too — everything is in place *except*
the OSRM backend itself. When it's time to turn this on:

- Deploy `integrations/osrm/docker-compose.yml` on ubuntu (osrm-backend
  container, Poland MLD graph, expose on 127.0.0.1:5001). One-time PBF
  download + extract/partition/customize takes ~30 min CPU, peaks ~3 GiB RAM
  (fits in ubuntu's 7.2 GiB).
- Drop `snap_interpolate.py` into `/opt/mototracker/` with a matching
  `/etc/mototracker/snap.env` (`OSRM_URL=http://127.0.0.1:5001`, DB creds).
- Install the same systemd timer as malinka (`OnUnitActiveSec=5min`).

Until then, ubuntu just stores raw rows; the UI renders them directly without
road-matching. Algorithm params are identical across both deployments
(MIN_GAP_M=40, MAX_GAP_M=5000, POINT_SPACING_M=25, MAX_POINTS_PER_GAP=10,
MAX_ROUTE_RATIO=3.0).

## Going public (further out)

Before opening this beyond invited friends:

- **TLS**: Let's Encrypt via `certbot --apache`. Requires a public DNS name
  pointing at the box.
- **Self-registration**: the current `login.php` is admin-seeded only. For
  public signup, add an `register.php` that creates a user (verified by email),
  then an optional Google/Apple OAuth layer on top — in that order, per the
  phased plan discussed.
- **Rate limiting / abuse**: `ingest_traccar.php` accepts any valid key; if
  keys leak, throttle per key. Apache's `mod_ratelimit` or fail2ban on the
  access log are the quick wins.
- **GDPR**: explicit consent at registration, account deletion endpoint,
  retention policy for `points` rows.

## Rollback

The ubuntu instance is fully self-contained. To tear down:

```bash
ssh ubuntu
sudo a2disconf mototracker
sudo systemctl reload apache2
sudo rm -rf /var/www/html/gpstrack
sudo mariadb <<'SQL'
DROP DATABASE IF EXISTS gps_db_data;
DROP USER IF EXISTS 'gps_db_dbuser'@'localhost';
SQL
```

Malinka is unaffected — same code, separate DB, separate API keys.

## Differences from malinka at a glance

| | malinka | ubuntu |
|---|---|---|
| Role | personal + admin | friends |
| HA / Traccar push | yes (`push-to-mototracker.py`) | no |
| OSRM pipeline | on HA side | ubuntu-side (stage 2) |
| Seeded devices | S_02 + HA_1..HA_6 (kept) | cleared (fresh) |
| Phone ingest shim | not used | `ingest_traccar.php` |
| Firmware ingest | in use (LilyGO) | available but unused |
| DB creds | malinka-local | ubuntu-local (separate) |
