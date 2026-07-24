# Telefon jako device (Podprojekt 4, PIVOT) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Telefon z aplikacją pojawia się w GŁÓWNYM widoku `devices`/mapie GPStrack jak sprzętowy tracker — jego trasy lądują w unijnej tabeli `points` pod auto-utworzonym device'em, a bogate dane per-przejazd żyją w tabeli `rides`.

**Architecture:** `api_routes.php` przestaje pisać do `app_routes`; zamiast tego (1) zapewnia istnienie device'a telefonu (`devices.code`=UUID instalacji), (2) upsertuje przejazd do `rides` (renamed `app_routes` + `device_id`), (3) eksploduje `pathJson` na wiersze `points` (`source='raw'`, `ride_id`), z **prędkością wyliczaną serwerowo z sąsiednich timestampów** i wysokością z `ele`. Główny widok (`pobierz_punkty.php`) i historia działają bez zmian. App dosyła tylko tożsamość urządzenia.

**Tech Stack:** PHP 7/8 + MySQL/MariaDB (mysqli, prepared statements) po stronie backendu/web; Kotlin + Hilt + DataStore + JUnit po stronie Androida.

## Global Constraints

- **Prepared statements wszędzie** w PHP (bez konkatenacji user-inputu do SQL).
- **Migracje idempotentne** (`IF NOT EXISTS` / `information_schema` guard), styl jak `migrations/003`–`008`.
- **Backend/web DoD = scenariusz `curl`** (`backend/tests/api_smoke.sh`); brak PHP unit-harnessu → status `🔬` do potwierdzenia na serwerze `192.168.1.145` (`ssh malinka`), potem `✅`.
- **LAN/http** — write_api_key/Bearer plaintextem jest akceptowalny (self-hosted LAN).
- **Commity backendu/web jako Artur** (`git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl'`), push do GitHub **po weryfikacji** na serwerze.
- Android: Kotlin bez nowych zależności; nowe pola z domyślnymi wartościami, by istniejące call-site'y kompilowały się bez zmian.
- Spec źródłowy: `docs/superpowers/specs/2026-07-24-phone-as-device-pivot-design.md`.

---

## File Structure

**Faza A — backend/web** (wykonanie bezpośrednie na serwerze/lokalnym PHP+MySQL):
- Create: `backend/migrations/009_phone_as_device.sql` — points += altitude/ride_id; app_routes→rides+device_id.
- Create: `backend/route_points.php` — współdzielony helper `parse_path_points()` (parsowanie pathJson + derywacja prędkości). Używany przez `api_routes.php` i backfill.
- Modify: `backend/api_routes.php` — ensure-device + upsert `rides` + explode→`points`.
- Modify: `backend/tests/api_smoke.sh` — nowe asercje (device utworzony, punkty wstawione, idempotencja, cudzy device 409).
- Create: `backend/tools/backfill_rides_to_points.php` — migracja istniejących tras do `points`.
- Modify/Delete (web): `backend/app_routes.html` (delete), `backend/pobierz_app_trasy.php` (delete), link w `backend/index.html`, sekcja grantu `app_user` w `backend/admin.html`.

**Faza B — Android** (przez repo; jeśli używana pętla `agent_workflow`, zadania mirrorować do `Android/.../BACKLOG.md`):
- Create: `app/src/main/java/com/mototracker/data/network/DeviceIdentity.kt` — interfejs + impl (UUID instalacji z DataStore, nazwa z `Build`).
- Modify: `app/src/main/java/com/mototracker/di/NetworkModule.kt` — binding `DeviceIdentity`.
- Modify: `app/src/main/java/com/mototracker/data/network/HttpGpStrackClient.kt` — dosyłanie `deviceCode`/`deviceName`.
- Test: `app/src/test/java/com/mototracker/data/network/DataStoreDeviceIdentityTest.kt`, oraz rozszerzenie `HttpGpStrackClientTest.kt`.

Ścieżki Android są względne do `Android/design_handoff_mototracker/`.

---

## Task A1: Migracja 009 — schema

**Files:**
- Create: `backend/migrations/009_phone_as_device.sql`

**Interfaces:**
- Produces: kolumny `points.altitude`, `points.ride_id` (+`idx_points_ride`); tabela `rides` (z `app_routes` przez rename, +`device_id`, +`idx_rides_device`).

- [ ] **Step 1: Napisz migrację**

Create `backend/migrations/009_phone_as_device.sql`:

```sql
-- 009: pivot "telefon jako device".
-- points += altitude, ride_id; app_routes -> rides (+device_id).
-- Idempotentne (IF NOT EXISTS + information_schema guard), jak 003-008.

-- 1. Kolumny per-punkt w points.
ALTER TABLE points
    ADD COLUMN IF NOT EXISTS altitude DOUBLE DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS ride_id  INT    DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_points_ride ON points(ride_id);

-- 2. app_routes -> rides: rename tylko gdy legacy istnieje, a rides jeszcze nie.
SET @has_app_routes = (SELECT COUNT(*) FROM information_schema.tables
                       WHERE table_schema = DATABASE() AND table_name = 'app_routes');
SET @has_rides = (SELECT COUNT(*) FROM information_schema.tables
                  WHERE table_schema = DATABASE() AND table_name = 'rides');
SET @sql = IF(@has_app_routes > 0 AND @has_rides = 0,
    'RENAME TABLE app_routes TO rides', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. Świeży install: utwórz rides jeśli nadal nieobecna (schemat = app_routes + device_id).
CREATE TABLE IF NOT EXISTS rides (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    client_uuid VARCHAR(64) NOT NULL,
    name VARCHAR(190) NOT NULL,
    started_at DATETIME DEFAULT NULL,
    km DOUBLE DEFAULT NULL,
    dur_sec INT DEFAULT NULL,
    avg_kmh DOUBLE DEFAULT NULL,
    max_kmh DOUBLE DEFAULT NULL,
    path_json LONGTEXT,
    payload_json LONGTEXT NOT NULL,
    device_id INT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_rides_user_uuid (user_id, client_uuid),
    INDEX idx_rides_user_started (user_id, started_at)
);

-- 4. device_id na rides (idempotentne; pokrywa gałąź z rename).
ALTER TABLE rides ADD COLUMN IF NOT EXISTS device_id INT DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_rides_device ON rides(device_id);
```

- [ ] **Step 2: Zastosuj migrację i zweryfikuj schemat**

Run (lokalny lub serwer — użyj tych samych credentiali co inne migracje, patrz `gps_track_config.php`):
```bash
mysql -u<user> -p<pass> <dbname> < backend/migrations/009_phone_as_device.sql
mysql -u<user> -p<pass> <dbname> -e "SHOW COLUMNS FROM points LIKE 'altitude'; SHOW COLUMNS FROM points LIKE 'ride_id'; SHOW COLUMNS FROM rides LIKE 'device_id'; SHOW TABLES LIKE 'app_routes';"
```
Expected: `altitude`, `ride_id`, `device_id` istnieją; `app_routes` **nie** istnieje (renamed do `rides`).

- [ ] **Step 3: Zweryfikuj idempotencję**

Run: `mysql -u<user> -p<pass> <dbname> < backend/migrations/009_phone_as_device.sql`
Expected: brak błędu (drugie odpalenie czyste; rename pominięty bo `rides` już istnieje).

- [ ] **Step 4: Commit**

```bash
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' add backend/migrations/009_phone_as_device.sql
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' commit -m "feat(backend): migracja 009 — points.altitude/ride_id + app_routes->rides (pivot)"
```

---

## Task A2: Helper `route_points.php` — parsowanie ścieżki + derywacja prędkości

**Files:**
- Create: `backend/route_points.php`

**Interfaces:**
- Produces: `parse_path_points(?string $pathJson): array` — zwraca listę `['lat'=>float,'lon'=>float,'ele'=>?float,'ts_ms'=>?int,'speed'=>?float]` w kolejności wejściowej; `speed` (km/h) wyliczone z sąsiednich punktów mających `ts_ms`. Obsługuje format obiektowy z appki (`{lat,lng,ele?,t?}`) ORAZ legacy pary (`[[lat,lng]]`).

- [ ] **Step 1: Napisz helper**

Create `backend/route_points.php`:

```php
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
            $ts  = array_key_exists('t', $e) && $e['t'] !== null ? (int)$e['t'] : null;
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
```

- [ ] **Step 2: Sanity-check parsera (CLI)**

Run:
```bash
php -r 'require "backend/route_points.php";
$p = parse_path_points("[{\"lat\":53.4,\"lng\":14.5,\"ele\":10,\"t\":1721560000000},{\"lat\":53.41,\"lng\":14.52,\"ele\":12,\"t\":1721560030000}]");
echo count($p)." pts; speed[1]=".$p[1]["speed"]."\n";
$l = parse_path_points("[[53.4,14.5],[53.41,14.52]]");
echo count($l)." legacy pts; speed[1]=".var_export($l[1]["speed"], true)."\n";'
```
Expected: `2 pts; speed[1]=<liczba ~ kilkadziesiąt>` (30 s między punktami → realna km/h); `2 legacy pts; speed[1]=NULL` (brak timestampów).

- [ ] **Step 3: Commit**

```bash
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' add backend/route_points.php
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' commit -m "feat(backend): route_points.php — parse pathJson + derywacja predkosci (pivot)"
```

---

## Task A3: Przeróbka `api_routes.php` — ensure-device + rides + explode→points

**Files:**
- Modify: `backend/api_routes.php` (całościowa przeróbka ciała po walidacji)
- Test: `backend/tests/api_smoke.sh` (asercje w Task A4)

**Interfaces:**
- Consumes: `parse_path_points()` z `route_points.php`; `$current_user`, `$auth_conn`, `auth_require_write()` z `auth.php`.
- Produces: HTTP `200 {"ok":true,"ride_id":N,"device_id":M,"points_inserted":K}`; `400 missing_fields` (brak `id`/`name`/`dateEpochMs`/`deviceCode`); `409 device_owned_by_other`; `401`/`405`/`500` jak dotąd. Tworzy/aktualizuje wiersz `devices`, upsertuje `rides`, wypełnia `points` (`source='raw'`, `ride_id`).

- [ ] **Step 1: Zastąp `api_routes.php`**

Replace `backend/api_routes.php` w całości:

```php
<?php
// Route ingest dla aplikacji MotoTracker (PIVOT: telefon jako device).
// POST application/json — cała nagrana trasa + tożsamość urządzenia:
//   { id, name, dateEpochMs, deviceCode, deviceName?, km, durSec, avg, max,
//     lean?, elev?, fuel?, bikeId?, wxJson?, pathJson?, speedJson?, notes? }
//
// Efekt: (1) ensure-device po deviceCode (auto-tworzenie telefonu),
//        (2) upsert do rides po (user_id, client_uuid),
//        (3) explode pathJson -> points (source='raw', ride_id) — telefon w glownym widoku.
// Auth: sesja (login/register) lub write_api_key Bearer via auth.php + auth_require_write().
// Sukces: 200 {"ok":true,"ride_id":N,"device_id":M,"points_inserted":K}

include('gps_track_config.php');
include('auth.php');       // 401+exit gdy brak auth; ustawia $current_user, $auth_conn
include('route_points.php');
auth_require_write();      // 403 dla read-only-token

header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'method_not_allowed']);
    exit;
}

$raw = file_get_contents('php://input');
$r = json_decode($raw, true);
if (!is_array($r)) {
    http_response_code(400);
    echo json_encode(['error' => 'invalid_json']);
    exit;
}

$client_uuid = trim((string)($r['id'] ?? ''));
$name        = trim((string)($r['name'] ?? ''));
$date_ms     = $r['dateEpochMs'] ?? null;
$device_code = trim((string)($r['deviceCode'] ?? ''));
$device_name = trim((string)($r['deviceName'] ?? ''));

if ($client_uuid === '' || $name === '' || !is_numeric($date_ms) || $device_code === '') {
    http_response_code(400);
    echo json_encode(['error' => 'missing_fields', 'detail' => 'id/name/dateEpochMs/deviceCode required']);
    exit;
}

$user_id    = (int)$current_user['id'];
$is_admin   = (int)($current_user['is_admin'] ?? 0);
$started_at = date('Y-m-d H:i:s', (int)((int)$date_ms / 1000));
$km         = (float)($r['km'] ?? 0);
$dur_sec    = (int)  ($r['durSec'] ?? 0);
$avg        = (float)($r['avg'] ?? 0);
$max        = (float)($r['max'] ?? 0);
$path_json  = isset($r['pathJson']) ? (string)$r['pathJson'] : null;
$payload    = $raw; // pełny obiekt (rich: lean/fuel/wx/notes) do rides.payload_json

// ── 1. Ensure-device ────────────────────────────────────────────────────────
$stmt = $auth_conn->prepare("SELECT id, user_id FROM devices WHERE code = ?");
$stmt->bind_param("s", $device_code);
$stmt->execute();
$dev_id = null; $dev_owner = null;
$stmt->bind_result($dev_id, $dev_owner);
$stmt->fetch();
$stmt->close();

if ($dev_id) {
    if (!$is_admin && (int)$dev_owner !== $user_id) {
        http_response_code(409);
        echo json_encode(['error' => 'device_owned_by_other']);
        exit;
    }
    if ($device_name !== '') {
        $u = $auth_conn->prepare("UPDATE devices SET name = ? WHERE id = ?");
        $u->bind_param("si", $device_name, $dev_id);
        $u->execute();
        $u->close();
    }
} else {
    // Kolor z małej palety, indeks po liczbie istniejących urządzeń (deterministyczny-ish).
    $palette = ['#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22', '#16a085'];
    $cnt = (int)($auth_conn->query("SELECT COUNT(*) AS c FROM devices")->fetch_assoc()['c'] ?? 0);
    $color = $palette[$cnt % count($palette)];
    $nm = $device_name !== '' ? $device_name : 'Telefon';
    $ins = $auth_conn->prepare("INSERT INTO devices (code, name, user_id, color, active) VALUES (?, ?, ?, ?, 1)");
    $ins->bind_param("ssis", $device_code, $nm, $user_id, $color);
    if (!$ins->execute()) {
        $ins->close();
        http_response_code(500);
        echo json_encode(['error' => 'device_create_failed']);
        exit;
    }
    $dev_id = $ins->insert_id;
    $ins->close();
}
$dev_id = (int)$dev_id;

// ── 2. Upsert ride ────────────────────────────────────────────────────────────
$stmt = $auth_conn->prepare(
    "INSERT INTO rides
        (user_id, client_uuid, name, started_at, km, dur_sec, avg_kmh, max_kmh, path_json, payload_json, device_id)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
        name = VALUES(name), started_at = VALUES(started_at), km = VALUES(km),
        dur_sec = VALUES(dur_sec), avg_kmh = VALUES(avg_kmh), max_kmh = VALUES(max_kmh),
        path_json = VALUES(path_json), payload_json = VALUES(payload_json), device_id = VALUES(device_id)"
);
$stmt->bind_param(
    "isssdiddssi",
    $user_id, $client_uuid, $name, $started_at, $km, $dur_sec, $avg, $max, $path_json, $payload, $dev_id
);
if (!$stmt->execute()) {
    $stmt->close();
    http_response_code(500);
    echo json_encode(['error' => 'insert_failed']);
    exit;
}
$ride_id = $stmt->insert_id;
$stmt->close();
if ($ride_id === 0) {
    $sel = $auth_conn->prepare("SELECT id FROM rides WHERE user_id = ? AND client_uuid = ?");
    $sel->bind_param("is", $user_id, $client_uuid);
    $sel->execute();
    $ride_id = (int)($sel->get_result()->fetch_assoc()['id'] ?? 0);
    $sel->close();
}
$ride_id = (int)$ride_id;

// ── 3. Explode pathJson -> points (transakcja, idempotentnie po ride_id) ───────
$points = parse_path_points($path_json);
$inserted = 0;
$auth_conn->begin_transaction();
try {
    // Usuń pochodne (snapped/interpolated) tego przejazdu, potem raw.
    $d1 = $auth_conn->prepare(
        "DELETE FROM points WHERE parent_id IN (SELECT id FROM (SELECT id FROM points WHERE ride_id = ?) t)"
    );
    $d1->bind_param("i", $ride_id);
    $d1->execute();
    $d1->close();

    $d2 = $auth_conn->prepare("DELETE FROM points WHERE ride_id = ?");
    $d2->bind_param("i", $ride_id);
    $d2->execute();
    $d2->close();

    if ($points) {
        $insP = $auth_conn->prepare(
            "INSERT INTO points (device_id, timestamp, lat, lon, speed, altitude, source, ride_id)
             VALUES (?, ?, ?, ?, ?, ?, 'raw', ?)"
        );
        foreach ($points as $p) {
            $ts = $p['ts_ms'] !== null ? date('Y-m-d H:i:s', (int)($p['ts_ms'] / 1000)) : $started_at;
            $insP->bind_param("isddddi", $dev_id, $ts, $p['lat'], $p['lon'], $p['speed'], $p['ele'], $ride_id);
            $insP->execute();
            $inserted++;
        }
        $insP->close();
    }
    $auth_conn->commit();
} catch (\Throwable $e) {
    $auth_conn->rollback();
    http_response_code(500);
    echo json_encode(['error' => 'points_write_failed']);
    exit;
}

echo json_encode(['ok' => true, 'ride_id' => $ride_id, 'device_id' => $dev_id, 'points_inserted' => $inserted]);
```

- [ ] **Step 2: Lint składni**

Run: `php -l backend/api_routes.php`
Expected: `No syntax errors detected in backend/api_routes.php`

- [ ] **Step 3: Commit**

```bash
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' add backend/api_routes.php
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' commit -m "feat(backend): api_routes.php pivot — ensure-device + rides + explode->points"
```

---

## Task A4: Smoke test — device/points/idempotencja/409

**Files:**
- Modify: `backend/tests/api_smoke.sh`

**Interfaces:**
- Consumes: endpointy `register.php`, `login.php`, `api_routes.php`, `pobierz_urzadzenia.php`, `pobierz_punkty.php`.

- [ ] **Step 1: Dodaj asercje pivotu na końcu `api_smoke.sh` (przed podsumowaniem `exit`)**

Dopisz do `backend/tests/api_smoke.sh` (po istniejących krokach; użyj już zdefiniowanych `post_json`, `check`, `$JAR`, `$BASE_URL`, `$WKEY`, `$EMAIL`, `$PASS`):

```bash
# ── PIVOT: telefon jako device ───────────────────────────────────────────────
DEV_CODE="smoke-dev-$(date +%s)"
RIDE_UUID="smoke-ride-$(date +%s)"
# dateEpochMs => data w strefie serwera; policz oczekiwaną datę tak jak PHP (lokalna).
RIDE_MS=1721560000000
RIDE_DATE=$(php -r 'echo date("Y-m-d", (int)(1721560000000/1000));')

pivot_route() { # $1 uuid $2 devcode
  printf '{"id":"%s","name":"Pivot trasa","dateEpochMs":%s,"deviceCode":"%s","deviceName":"samsung SM-TEST","km":1.2,"durSec":40,"avg":30,"max":60,"pathJson":"[{\\"lat\\":53.40,\\"lng\\":14.50,\\"ele\\":10,\\"t\\":1721560000000},{\\"lat\\":53.401,\\"lng\\":14.502,\\"ele\\":12,\\"t\\":1721560030000},{\\"lat\\":53.402,\\"lng\\":14.504,\\"ele\\":13,\\"t\\":1721560060000}]"}' "$1" "$2" "$3"
}

# fresh session (login by e-mail)
: > "$JAR"
post_json "$BASE_URL/login.php" "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" >/dev/null

# upload z tożsamością urządzenia -> 200, auto-tworzy device
code=$(post_json "$BASE_URL/api_routes.php" "$(pivot_route "$RIDE_UUID" "$DEV_CODE")")
check "pivot upload" 200 "$code" "$(cat /tmp/smoke_body)"

# device pojawia się na liście urządzeń
dev_list=$(curl -s -b "$JAR" "$BASE_URL/pobierz_urzadzenia.php")
echo "$dev_list" | grep -q "$DEV_CODE" \
  && echo "PASS  device widoczny w pobierz_urzadzenia" \
  || { echo "FAIL  device brak w pobierz_urzadzenia — $dev_list"; fails=$((fails+1)); }

# punkty wstawione (3) dla tego device w dacie trasy
pts=$(curl -s -b "$JAR" "$BASE_URL/pobierz_punkty.php?device=$DEV_CODE&date=$RIDE_DATE")
pcount=$(echo "$pts" | grep -o '"lat"' | wc -l | tr -d ' ')
[ "$pcount" -ge 3 ] \
  && echo "PASS  3 punkty wstawione ($pcount)" \
  || { echo "FAIL  oczekiwano >=3 punktów, jest $pcount — $pts"; fails=$((fails+1)); }

# re-upload tego samego ride_uuid -> 200, punkty NIE duplikują się
code=$(post_json "$BASE_URL/api_routes.php" "$(pivot_route "$RIDE_UUID" "$DEV_CODE")")
check "pivot re-upload" 200 "$code"
pts2=$(curl -s -b "$JAR" "$BASE_URL/pobierz_punkty.php?device=$DEV_CODE&date=$RIDE_DATE")
pcount2=$(echo "$pts2" | grep -o '"lat"' | wc -l | tr -d ' ')
[ "$pcount2" -eq "$pcount" ] \
  && echo "PASS  idempotencja: bez duplikatów ($pcount2)" \
  || { echo "FAIL  duplikacja punktów: było $pcount, jest $pcount2"; fails=$((fails+1)); }

# drugi user nie może uploadować pod cudzy deviceCode -> 409
EMAIL2="smoke2+$(date +%s)@example.com"
: > "$JAR"
post_json "$BASE_URL/register.php" "{\"email\":\"$EMAIL2\",\"password\":\"$PASS\"}" >/dev/null
code=$(post_json "$BASE_URL/api_routes.php" "$(pivot_route "other-$RIDE_UUID" "$DEV_CODE")")
check "cudzy device -> 409" 409 "$code"
```

Upewnij się, że końcowe podsumowanie (`if [ "$fails" ... ]; exit`) jest po tych krokach.

- [ ] **Step 2: Uruchom smoke test przeciw działającemu backendowi**

Run: `BASE_URL=http://localhost/gpstrack bash backend/tests/api_smoke.sh` (lub `http://192.168.1.145/gpstrack` na serwerze)
Expected: wszystkie linie `PASS`, w tym „pivot upload", „device widoczny", „3 punkty wstawione", „idempotencja", „cudzy device -> 409"; skrypt kończy się kodem 0.

- [ ] **Step 3: Commit**

```bash
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' add backend/tests/api_smoke.sh
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' commit -m "test(backend): smoke — pivot device/points/idempotencja/409"
```

---

## Task A5: Backfill istniejących tras → points

**Files:**
- Create: `backend/tools/backfill_rides_to_points.php`

**Interfaces:**
- Consumes: `parse_path_points()`; `gps_track_config.php` (poświadczenia DB).
- Produces: dla każdego wiersza `rides` bez `device_id` — syntetyczny device `code='app-import-<user_id>'` (`name='Telefon (import)'`), ustawia `rides.device_id`, wypełnia `points` (`source='raw'`, `ride_id`). Idempotentny (delete-then-insert po `ride_id`).

- [ ] **Step 1: Napisz skrypt**

Create `backend/tools/backfill_rides_to_points.php`:

```php
<?php
// Jednorazowy, idempotentny backfill: konwertuje istniejące wiersze `rides`
// (dawne app_routes) na wiersze `points` pod syntetycznym device'em telefonu.
// Uruchamiać z CLI:  php backend/tools/backfill_rides_to_points.php
//
// Stary model app_routes był per-user (brak per-install), więc tworzymy jeden
// device "Telefon (import)" na użytkownika (code = app-import-<user_id>).

require __DIR__ . '/../gps_track_config.php';
require __DIR__ . '/../route_points.php';

$conn = new mysqli($gps_db_host, $gps_db_user, $gps_db_pass, $gps_db_name);
if ($conn->connect_error) { fwrite(STDERR, "DB connect error\n"); exit(1); }

// Cache: user_id -> device_id (import).
$importDev = [];
function import_device_for_user(mysqli $conn, array &$cache, int $userId): int {
    if (isset($cache[$userId])) return $cache[$userId];
    $code = 'app-import-' . $userId;
    $sel = $conn->prepare("SELECT id FROM devices WHERE code = ?");
    $sel->bind_param("s", $code); $sel->execute();
    $id = (int)($sel->get_result()->fetch_assoc()['id'] ?? 0); $sel->close();
    if ($id === 0) {
        $name = 'Telefon (import)';
        $color = '#95a5a6';
        $ins = $conn->prepare("INSERT INTO devices (code, name, user_id, color, active) VALUES (?, ?, ?, ?, 1)");
        $ins->bind_param("ssis", $code, $name, $userId, $color);
        $ins->execute(); $id = $ins->insert_id; $ins->close();
    }
    $cache[$userId] = $id;
    return $id;
}

$rows = $conn->query("SELECT id, user_id, started_at, path_json FROM rides ORDER BY id");
$ridesN = 0; $ptsN = 0;
while ($ride = $rows->fetch_assoc()) {
    $rideId  = (int)$ride['id'];
    $userId  = (int)$ride['user_id'];
    $started = $ride['started_at'] ?: date('Y-m-d H:i:s');
    $devId   = import_device_for_user($conn, $importDev, $userId);

    $conn->query("UPDATE rides SET device_id = $devId WHERE id = $rideId AND device_id IS NULL");

    $points = parse_path_points($ride['path_json']);
    // idempotencja: skasuj poprzednie punkty tego przejazdu
    $del = $conn->prepare("DELETE FROM points WHERE ride_id = ?");
    $del->bind_param("i", $rideId); $del->execute(); $del->close();

    if ($points) {
        $insP = $conn->prepare(
            "INSERT INTO points (device_id, timestamp, lat, lon, speed, altitude, source, ride_id)
             VALUES (?, ?, ?, ?, ?, ?, 'raw', ?)"
        );
        foreach ($points as $p) {
            $ts = $p['ts_ms'] !== null ? date('Y-m-d H:i:s', (int)($p['ts_ms'] / 1000)) : $started;
            $insP->bind_param("isddddi", $devId, $ts, $p['lat'], $p['lon'], $p['speed'], $p['ele'], $rideId);
            $insP->execute(); $ptsN++;
        }
        $insP->close();
    }
    $ridesN++;
}
echo "Backfill: $ridesN rides -> $ptsN points\n";
$conn->close();
```

- [ ] **Step 2: Lint + uruchom (na serwerze z danymi)**

Run: `php -l backend/tools/backfill_rides_to_points.php && php backend/tools/backfill_rides_to_points.php`
Expected: `No syntax errors`; potem `Backfill: <N> rides -> <M> points` (N>=1 gdy istnieją stare trasy, np. 47).

- [ ] **Step 3: Zweryfikuj idempotencję i widoczność**

Run: `php backend/tools/backfill_rides_to_points.php`
Expected: ta sama liczba punktów `M` (delete-then-insert, bez narastania). W przeglądarce (VPN) na koncie właściciela stara trasa pojawia się w głównym widoku pod „Telefon (import)".

- [ ] **Step 4: Commit**

```bash
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' add backend/tools/backfill_rides_to_points.php
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' commit -m "feat(backend): backfill starych tras (rides) -> points pod device importu"
```

---

## Task A6: Usunięcie widoku W5 + neutralizacja grantu `app_user`

**Files:**
- Delete: `backend/app_routes.html`, `backend/pobierz_app_trasy.php`
- Modify: `backend/index.html` (usuń link do „Przejazdy z aplikacji")
- Modify: `backend/admin.html` (usuń opcję grantu typu `app_user`; zostaw `device`)

**Interfaces:**
- Produces: brak widoku W5; główny widok devices/mapa bez zmian; granty typu `device` działają dla telefonu (zwykły device).

- [ ] **Step 1: Zlokalizuj referencje do W5**

Run: `grep -rn "app_routes.html\|pobierz_app_trasy\|Przejazdy z aplikacji\|app_user" backend/*.html backend/admin/ 2>/dev/null`
Expected: lista miejsc (link w `index.html`, sekcja/opcja w `admin.html`, ew. `admin/*grant*`).

- [ ] **Step 2: Usuń pliki W5**

Run: `git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' rm backend/app_routes.html backend/pobierz_app_trasy.php`
Expected: oba pliki usunięte ze śledzenia.

- [ ] **Step 3: Usuń link z `index.html` i opcję `app_user` z `admin.html`**

Usuń z `backend/index.html` element linkujący do `app_routes.html` (kotwica/przycisk „Przejazdy z aplikacji"). W `backend/admin.html` w modalu grantów usuń wybór typu zasobu `app_user` (pozostaw `device`); jeśli JS iteruje po typach — usuń `app_user` z tablicy typów. Nie ruszaj endpointów `admin/*grant*` (typ `device` nadal używany); `app_user` staje się martwy, ale nieszkodliwy.

- [ ] **Step 4: Weryfikacja w przeglądarce**

Sprawdź (VPN/Playwright): `index.html` nie ma już linku do W5; `app_routes.html` zwraca 404; główny widok devices/mapa działa; telefon (po Task A3/A5) widoczny jak device; modal grantów w `admin.html` oferuje tylko `device`.
Expected: powyższe potwierdzone; brak błędów JS w konsoli.

- [ ] **Step 5: Commit**

```bash
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' add -A backend/index.html backend/admin.html
git -c user.name='Artur' -c user.email='R_E_D_O_X@wp.pl' commit -m "feat(web): usuniecie widoku W5 (app_routes) + neutralizacja grantu app_user (pivot)"
```

---

## Task B1: Android — `DeviceIdentity` (UUID instalacji + nazwa z Build)

**Files:**
- Create: `app/src/main/java/com/mototracker/data/network/DeviceIdentity.kt`
- Modify: `app/src/main/java/com/mototracker/di/NetworkModule.kt`
- Test: `app/src/test/java/com/mototracker/data/network/DataStoreDeviceIdentityTest.kt`

**Interfaces:**
- Produces: `interface DeviceIdentity { suspend fun code(): String; fun name(): String }`. `code()` zwraca stabilny UUID instalacji (generowany raz, utrwalany w DataStore pod kluczem `device_install_uuid`, ten sam przy kolejnych wywołaniach). `name()` = `Build.MANUFACTURER + " " + Build.MODEL`.

- [ ] **Step 1: Napisz test persystencji `code()`**

Create `app/src/test/java/com/mototracker/data/network/DataStoreDeviceIdentityTest.kt`:

```kotlin
package com.mototracker.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreDeviceIdentityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { tmp.newFile("id_${System.nanoTime()}.preferences_pb") }

    @Test
    fun `code generuje UUID i zwraca ten sam przy kolejnym wywolaniu`() = runTest {
        val store = newStore()
        val id = DataStoreDeviceIdentity(store, deviceName = "test dev")
        val first = id.code()
        val second = id.code()
        assertTrue("kod nie jest pusty", first.isNotBlank())
        assertEquals("kod stabilny między wywołaniami", first, second)
    }

    @Test
    fun `name zwraca wstrzykniete Build-owe zrodlo`() {
        val id = DataStoreDeviceIdentity(newStore(), deviceName = "samsung SM-G991B")
        assertEquals("samsung SM-G991B", id.name())
    }
}
```

- [ ] **Step 2: Uruchom test — ma się nie kompilować/failować**

Run: `./gradlew :app:testDebugUnitTest --tests "*DataStoreDeviceIdentityTest*"`
Expected: FAIL — `DeviceIdentity`/`DataStoreDeviceIdentity` nie istnieją.

- [ ] **Step 3: Zaimplementuj `DeviceIdentity.kt`**

Create `app/src/main/java/com/mototracker/data/network/DeviceIdentity.kt`:

```kotlin
package com.mototracker.data.network

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tożsamość tego telefonu jako urządzenia GPStrack.
 *
 * [code] to stabilny identyfikator instalacji (UUID), generowany raz i utrwalany.
 * [name] to czytelna nazwa modelu telefonu wysyłana do serwera przy uploadzie.
 */
interface DeviceIdentity {
    /** Stabilny UUID instalacji (ten sam przy kolejnych wywołaniach). */
    suspend fun code(): String

    /** Nazwa urządzenia, np. "samsung SM-G991B". */
    fun name(): String
}

/**
 * [DeviceIdentity] utrwalający UUID instalacji w singletonowym [DataStore]<[Preferences]>
 * (ten sam store co [DataStoreSessionStore]), pod dedykowanym kluczem `device_install_uuid`.
 *
 * @param dataStore  Singletonowy Preferences DataStore.
 * @param deviceName Nazwa urządzenia; w produkcji z [Build] (patrz NetworkModule).
 */
@Singleton
class DataStoreDeviceIdentity @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val deviceName: String,
) : DeviceIdentity {

    private object Keys {
        val INSTALL_UUID = stringPreferencesKey("device_install_uuid")
    }

    override suspend fun code(): String {
        val existing = dataStore.data.first()[Keys.INSTALL_UUID]
        if (existing != null && existing.isNotBlank()) return existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { it[Keys.INSTALL_UUID] = generated }
        return generated
    }

    override fun name(): String = deviceName
}
```

- [ ] **Step 4: Uruchom test — ma przejść**

Run: `./gradlew :app:testDebugUnitTest --tests "*DataStoreDeviceIdentityTest*"`
Expected: PASS (oba testy).

- [ ] **Step 5: Dodaj binding w `NetworkModule.kt`**

W `NetworkModule.kt` dodaj import `android.os.Build`, `com.mototracker.data.network.DeviceIdentity`, `com.mototracker.data.network.DataStoreDeviceIdentity`, `androidx.datastore.core.DataStore`, `androidx.datastore.preferences.core.Preferences`, `dagger.Provides`. `DataStoreDeviceIdentity` wymaga `String` (nazwa), więc dostarcz go przez `@Provides` (nie `@Binds`):

```kotlin
    /** Dostarcza [DeviceIdentity] z nazwą modelu z [Build] i UUID instalacji z DataStore. */
    companion object {
        @Provides
        @Singleton
        fun provideDeviceIdentity(dataStore: DataStore<Preferences>): DeviceIdentity =
            DataStoreDeviceIdentity(
                dataStore = dataStore,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
    }
```

(Jeśli w `NetworkModule` — klasie `abstract` — brak jeszcze `companion object`, dodaj powyższy. `@Provides` musi być w `companion object` wewnątrz modułu `abstract`.)

- [ ] **Step 6: Zbuduj — DI się kompiluje**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (graf Hilt kompletny).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mototracker/data/network/DeviceIdentity.kt app/src/main/java/com/mototracker/di/NetworkModule.kt app/src/test/java/com/mototracker/data/network/DataStoreDeviceIdentityTest.kt
git commit -m "feat(app): DeviceIdentity — UUID instalacji + nazwa z Build (pivot)"
```

---

## Task B2: Android — upload dosyła `deviceCode`/`deviceName`

**Files:**
- Modify: `app/src/main/java/com/mototracker/data/network/HttpGpStrackClient.kt`
- Test: `app/src/test/java/com/mototracker/data/network/HttpGpStrackClientTest.kt`

**Interfaces:**
- Consumes: `DeviceIdentity` z Task B1 (`code()`, `name()`).
- Produces: `HttpGpStrackClient.uploadRoute` dokłada do ciała JSON pola `deviceCode` i `deviceName`.

- [ ] **Step 1: Dodaj `FakeDeviceIdentity` i test do `HttpGpStrackClientTest.kt`**

W sekcji „Fakes" `HttpGpStrackClientTest.kt` dodaj:

```kotlin
private class FakeDeviceIdentity(
    private val code: String = "install-uuid-test",
    private val name: String = "samsung SM-TEST",
) : DeviceIdentity {
    override suspend fun code(): String = code
    override fun name(): String = name
}
```

W bloku `@Before setUp()` przekaż fake do konstruktora klienta (patrz Step 3 — konstruktor zyskuje parametr `deviceIdentity`). Dodaj test w sekcji `// ── uploadRoute ──`:

```kotlin
    @Test
    fun `uploadRoute dokłada deviceCode i deviceName do ciała`() = runTest {
        sessionStore.save(cookie = "PHPSESSID=abc", email = "a@b.c", writeApiKey = null)
        transport.nextResponse = HttpResponse(code = 200, headers = emptyMap(), body = "")

        client.uploadRoute(SERVER, minimalRoute)

        val body = String(transport.lastRequest!!.body!!, Charsets.UTF_8)
        val json = org.json.JSONObject(body)
        assertEquals("install-uuid-test", json.getString("deviceCode"))
        assertEquals("samsung SM-TEST", json.getString("deviceName"))
    }
```

- [ ] **Step 2: Uruchom test — ma failować**

Run: `./gradlew :app:testDebugUnitTest --tests "*HttpGpStrackClientTest*"`
Expected: FAIL — konstruktor nie przyjmuje `deviceIdentity` / brak `deviceCode` w ciele.

- [ ] **Step 3: Rozszerz `HttpGpStrackClient`**

W `HttpGpStrackClient.kt` dodaj `deviceIdentity` do konstruktora:

```kotlin
class HttpGpStrackClient @Inject constructor(
    private val transport: HttpTransport,
    private val sessionStore: SessionStore,
    private val deviceIdentity: DeviceIdentity,
) : GpStrackClient {
```

W `uploadRoute`, przed budową ciała, pobierz tożsamość i dołóż do JSON. Zmień początek bloku `runCatching`:

```kotlin
            runCatching {
                val session = sessionStore.session.first()
                val json = buildJson(route).apply {
                    put("deviceCode", deviceIdentity.code())
                    put("deviceName", deviceIdentity.name())
                }
                val body = json.toString().toByteArray(Charsets.UTF_8)
```

(reszta `uploadRoute` bez zmian — `body` nadal wysyłane jak wcześniej).

- [ ] **Step 4: Uruchom testy — mają przejść**

Run: `./gradlew :app:testDebugUnitTest --tests "*HttpGpStrackClientTest*"`
Expected: PASS (nowy test + wszystkie dotychczasowe uploadRoute — konstruktor z fake przekazany w `setUp`).

- [ ] **Step 5: Pełny build + testy sieci**

Run: `./gradlew :app:testDebugUnitTest --tests "*network*" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; wszystkie testy sieciowe zielone.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mototracker/data/network/HttpGpStrackClient.kt app/src/test/java/com/mototracker/data/network/HttpGpStrackClientTest.kt
git commit -m "feat(app): upload dosyła deviceCode/deviceName (pivot)"
```

---

## Weryfikacja E2E (po Fazie A+B)

- [ ] Na urządzeniu (P20): nagraj krótką trasę → sync → w webie (VPN) telefon pojawia się w **głównym** widoku devices z nazwą modelu, a trasa rysuje się na mapie/historii pod tą datą.
- [ ] `pobierz_urzadzenia.php` (sesja właściciela) zawiera telefon; `pobierz_punkty.php?device=<uuid>&date=<data>` zwraca punkty z `speed`/`altitude`.
- [ ] Grant typu `device` na telefon nadany innemu kontu → to konto widzi telefon w głównym widoku (a bez grantu — nie).
- [ ] Push do GitHub (backend/web jako Artur) **po** potwierdzeniu na serwerze; aktualizacja `backend/BACKLOG.md` Podprojekt 4 na `✅`/`🔬`.

---

## Self-Review (wykonane przy pisaniu planu)

- **Pokrycie spec:** migracja 009 (§Model danych) → A1; helper+derywacja prędkości (§Ingest, refinement) → A2; przeróbka `api_routes.php` ensure-device/rides/explode (§Ingest) → A3; idempotencja+OSRM (delete children+raw) → A3 Step 1; smoke (§DoD) → A4; backfill (§Migracja) → A5; usunięcie W5 + grant app_user (§Web) → A6; install UUID + nazwa Build (§Aplikacja) → B1; deviceCode/deviceName w uploadzie (§Aplikacja) → B2. Per-punkt `s` z appki **świadomie zastąpione** derywacją serwerową (patrz nagłówek + A2) — altitude nadal z `ele` (bez zmian w appce).
- **Placeholdery:** brak TBD/TODO; każdy krok z kodem/komendą i oczekiwanym wyndikiem.
- **Spójność typów:** `parse_path_points()` zwraca klucze `lat/lon/ele/ts_ms/speed` — używane identycznie w A3 i A5; bind `"isddddi"` (points) i `"isssdiddssi"` (rides) policzone; `DeviceIdentity.code()/name()` zgodne w B1 impl, B1 test, B2 klient i B2 test.
