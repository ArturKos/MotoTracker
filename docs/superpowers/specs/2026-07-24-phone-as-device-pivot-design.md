# Pivot: telefon jako urządzenie (device) w głównym widoku GPStrack

**Data:** 2026-07-24
**Status:** zatwierdzony
**Podprojekt 4 (PIVOT)** integracji app↔GPStrack. Kontekst: [[mototracker-gpstrack-integration]].
Poprzedniki: backend BE1–BE6 ✅, app AH/AI ✅, web W0–W5 ✅ (E2E potwierdzony na P20 2026-07-24).

## Cel

Telefon z aplikacją MotoTracker ma pojawiać się w **GŁÓWNYM widoku `devices`/mapie**
GPStrack — tak samo jak sprzętowe trackery przypisane do użytkownika — **zamiast**
osobnego widoku „Przejazdy z aplikacji" (W5). Trasy nagrane w appce mają być widoczne
w głównej mapie i historii (po dacie), z nazwą urządzenia pobraną z telefonu.

Zachowanie docelowe (decyzja Akosa): **„urządzenie z historią"** — telefon widnieje jako
device, jego przejechane trasy/historia są widoczne w głównym widoku. **Live-streaming
podczas jazdy NIE jest wymagany** (upload wsadowy po jeździe jest OK).

## Decyzje (zatwierdzone)

1. **Architektura: A — points-device.** Serwer eksploduje trasy z appki na wiersze w
   unijnej tabeli `points` pod auto-utworzonym `device` telefonu. Telefon = tracker w
   głównym widoku „za darmo" (mapa/historia/GPX/snap-interpolate OSRM działają bez nowego
   kodu renderującego, bo wszystko czyta `points`).
2. **Dane per-przejazd** (nazwa, paliwo, pogoda, notatki, avg/max, lean) → **cienka tabela
   `rides`** (powstała z `app_routes`), połączona z `points` przez `points.ride_id`. NIE
   duplikujemy tych danych na każdym wierszu punktu.
3. **Jedno urządzenie na instalację/telefon.** Stabilny UUID instalacji (wygenerowany raz,
   trzymany w DataStore) → `devices.code`. Wymiana telefonu/reinstalacja = nowy device.
4. **Nazwa urządzenia** = `Build.MANUFACTURER + " " + Build.MODEL` (np. „samsung SM-G991B"),
   edytowalna później generycznie w adminie (device jak każdy inny).
5. **W5 usunięty całkowicie** — telefon żyje w głównym widoku. Dane `rides` zostają w bazie
   (przyszły drilldown/GPX), ale bez dedykowanego UI w tym zakresie.
6. **Istniejące `app_routes` migrowane** do `rides`+`points` (stare trasy pojawią się w
   głównym widoku).
7. Wykonanie: backend+web **bezpośrednio** na serwerze `malinka` (poza androidową pętlą),
   jako Artur, push do GitHub **po weryfikacji**; zmiany w appce przez repo (mogą wejść w
   androidową pętlę agent_workflow, jeśli trafią do `Android/.../BACKLOG.md`).

## Model danych (migracja `009`)

### `points` — rozszerzenie
```sql
ALTER TABLE points
    ADD COLUMN IF NOT EXISTS altitude DOUBLE DEFAULT NULL,   -- z TrackPoint.ele
    ADD COLUMN IF NOT EXISTS ride_id  INT    DEFAULT NULL;   -- FK→rides.id (raw punkty przejazdu)
CREATE INDEX IF NOT EXISTS idx_points_ride ON points(ride_id);
```
(Idempotentne, w stylu istniejących migracji 003/005/006.)

### `rides` — z `app_routes` przez rename (zachowuje 47 istniejących tras)
```sql
-- zachowaj dane: rename zamiast create+copy
ALTER TABLE app_routes RENAME TO rides;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS device_id INT DEFAULT NULL;  -- FK→devices.id
CREATE INDEX IF NOT EXISTS idx_rides_device ON rides(device_id);
```
Kolumny `rides` (po rename): `id, user_id, client_uuid, name, started_at, km, dur_sec,
avg_kmh, max_kmh, path_json, payload_json (rich: lean/fuel/wx/notes/…), created_at,
updated_at, device_id`, `UNIQUE(user_id, client_uuid)`. `payload_json` pozostaje surowym
źródłem bogatych metadanych per-przejazd (parsowane on-demand w przyszłym drilldownie).

**Uwaga migracyjna:** jeśli w środowisku brak jeszcze tabeli `app_routes` (świeży install),
migracja tworzy `rides` od zera z pełnym schematem (`CREATE TABLE IF NOT EXISTS rides …`).
Migracja musi obsłużyć oba przypadki idempotentnie.

### `devices` — bez zmian schematu
Telefon to zwykły wiersz: `code`=UUID instalacji, `name`=Build, `user_id`=właściciel,
`color`=z palety, `active=1`. Żadnych specjalnych flag „to telefon" (spójność z trackerami).

## Ingest — przeróbka `api_routes.php`

Zapis trasy z appki przestaje trafiać do `app_routes`; zamiast tego zasila `rides`+`points`.

**Payload (JSON, POST)** — rozszerzony o tożsamość urządzenia i prędkość per-punkt:
- istniejące pola trasy: `id` (client_uuid), `name`, `dateEpochMs`, `km`, `durSec`, `avg`,
  `max`, `lean`, `elev`, `fuel`, `bikeId`, `wxJson`, `pathJson`, `speedJson`,
  `elevProfileJson`, `notes` (bez zmian — trafiają do `rides` / `payload_json`).
- **nowe:** `deviceCode` (UUID instalacji), `deviceName` (`Build.MANUFACTURER + " " + MODEL`).
- **`pathJson` punkty zyskują `s`** (prędkość, km/h) obok `{lat,lng,ele,t}`.

**Logika endpointu** (auth: Bearer `write_api_key` lub sesja → `$current_user`; brak → 401;
metoda≠POST → 405; brak `id`/`name`/`dateEpochMs`/`deviceCode` → 400):

1. **Ensure-device.** `SELECT id,user_id FROM devices WHERE code = deviceCode`.
   - brak → `INSERT INTO devices (code, name, user_id, color, active) VALUES (deviceCode,
     deviceName, current_user, <kolor z palety>, 1)`.
   - istnieje, `user_id ≠ current_user` i nie-admin → `409 {"error":"device_owned_by_other"}`.
   - istnieje, moje → użyj `id`; odśwież `name` gdy `deviceName` niepuste.
2. **Upsert ride.** `INSERT … ON DUPLICATE KEY UPDATE …` do `rides` po `(user_id, client_uuid)`,
   ustaw `device_id`, `payload_json`=surowe ciało (jak dotąd). Zwróć `rides.id`.
3. **Explode → points** (w **transakcji**, idempotentnie po `ride_id`):
   - `DELETE FROM points WHERE ride_id = <ride.id>` — usuwa poprzednie punkty tego przejazdu
     ORAZ ich pochodne snapped/interpolated (patrz „Idempotencja i OSRM" niżej).
   - dla każdego punktu z `pathJson`: `INSERT INTO points (device_id, timestamp, lat, lon,
     speed, altitude, source, ride_id) VALUES (device_id, FROM t, lat, lng, s, ele, 'raw',
     ride.id)`.
4. `200 {"ok":true, "ride_id":N, "device_id":M, "points_inserted":K}`. Błąd DB → `500`
   (bez wycieku), rollback transakcji.

Prepared statements wszędzie. **Zgodność wstecz:** brak `s`/`ele` w starszym `pathJson` →
`speed`/`altitude` = `NULL` (mapa toleruje null speed).

### Idempotencja i OSRM snap/interpolate
`ride_id` niosą **tylko punkty `source='raw'`**. Worker OSRM (`pull`/korekta na serwerze)
tworzy wiersze `source IN ('snapped','interpolated')` z `parent_id` wskazującym raw-punkt.
Przy re-uploadzie tego samego przejazdu należy usunąć zarówno raw punkty tego `ride_id`,
jak i ich pochodne:
```sql
DELETE FROM points WHERE parent_id IN (SELECT id FROM (SELECT id FROM points WHERE ride_id = ?) t);
DELETE FROM points WHERE ride_id = ?;
```
(kolejność: najpierw pochodne, potem raw — FK/parent_id spójność). Worker odtworzy korektę
z nowych raw-punktów.

### Timezone punktów
`points.timestamp` (DATETIME) zapisujemy konwencją istniejącego ingestu firmware'owego
(`gps_tracker_add_data_to_db.php` → `date('Y-m-d H:i:s', …)`, czas lokalny serwera).
`dateEpochMs`/`t` z appki są w epoch-ms UTC → `date('Y-m-d H:i:s', t/1000)` (strefa serwera,
spójnie z resztą). Główny widok filtruje `DATE(p.timestamp)=?` — akceptowalny drobny efekt
graniczny o północy (jak dla trackerów). Bez dodatkowej logiki TZ w tym zakresie.

## Aplikacja (Android)

Minimalne zmiany; bogate dane lokalne bez zmian (serwer trzyma je w `rides.payload_json`).

- **Install UUID:** wygeneruj raz (`UUID.randomUUID()`), utrwal w DataStore, czytaj jako
  `deviceCode`. Prywatnie i stabilnie (nie ANDROID_ID).
- **Nazwa urządzenia:** `Build.MANUFACTURER + " " + Build.MODEL` → `deviceName`.
- **Per-punkt speed:** `TrackPoint` + serializacja `pathJson`
  (`DataStoreRecordingSessionStore`/miejsce budowy `pathJson`) zyskują pole `s` (z
  `LocationSample.speedMps`, przeliczone na km/h). `TrackGeometry.parsePathJsonFull` czyta
  `s` tolerancyjnie (brak → null).
- **Upload:** `HttpGpStrackClient.buildJson`/`uploadRoute` dokłada `deviceCode` i
  `deviceName` do ciała POST na `api_routes.php`.
- Testy jednostkowe transportu rozszerzone o nowe pola (wzór istniejących
  `HttpGpStrackClient*Test`).

## Migracja istniejących `app_routes` → rides+points (skrypt PHP backfill)

Jednorazowy, idempotentny skrypt (bo eksplozja `path_json`→`points` wymaga parsowania JSON,
którego SQL na starszym MariaDB nie zrobi wygodnie):

Dla każdego wiersza `rides` (dawne `app_routes`) bez `device_id`:
1. **Ensure-device per user (import):** stary model był per-user (brak per-install), więc
   utwórz raz na usera device `code = "app-import-<user_id>"`, `name = "Telefon (import)"`.
2. `UPDATE rides SET device_id = <dev>` dla tras tego usera.
3. Parsuj `path_json`, `INSERT` do `points` (`source='raw'`, `ride_id`, `timestamp` z `t`
   gdy jest — inaczej rozłóż równomiernie w `[started_at, started_at+dur_sec]`; `speed`
   wylicz z sąsiednich punktów haversine/Δt lub `NULL`; `altitude` z `ele` gdy jest).
   Idempotentnie: `DELETE FROM points WHERE ride_id=?` przed re-insertem.

**DoD migracji:** po odpaleniu stare 47 tras widoczne w głównym widoku pod device
„Telefon (import)"; ponowne odpalenie nie duplikuje punktów.

## Web

- **Usuń W5:** `app_routes.html`, link z głównego frontu, `pobierz_app_trasy.php`. Telefon
  jest teraz w głównym widoku devices/mapie (`pobierz_punkty.php`, `pobierz_urzadzenia.php`)
  — bez zmian w tych endpointach (telefon to zwykły device z punktami).
- **Filtry uprawnień:** `view_grants` typ `device` obejmuje telefon automatycznie (zwykły
  device). Grant typ `app_user` staje się bezużyteczny (app_routes znika jako źródło widoku)
  — do usunięcia/zignorowania: usuń sekcję app_user z admin UI grantów lub zostaw martwą
  (decyzja porządkowa, nie blokuje pivotu).

## Poza zakresem (YAGNI teraz)

- Live-streaming pozycji telefonu podczas jazdy.
- Webowy widok szczegółu przejazdu z `rides` (drilldown lista→mapa+statystyki). Dane są
  zachowane w `rides`/`payload_json` na przyszłość.
- Edycja nazwy/koloru telefonu w adminie (działa generycznie, jeśli admin już to ma dla
  device'ów).
- Grant typu `app_user` (do usunięcia w ramach porządków, nie funkcjonalności).

## Definition of Done (całość)

- Migracja `009` aplikuje się czysto na serwerze (`points.altitude`/`ride_id` istnieją;
  `app_routes`→`rides`+`device_id`; FK trzymają). Idempotentna.
- `api_routes.php`: upload z appki tworzy/odnajduje device telefonu, upsertuje `rides`,
  eksploduje na `points`; re-upload tego samego `client_uuid` nie duplikuje punktów;
  cudzy device → 409; scenariusz `curl`/smoke przechodzi.
- Backfill: stare trasy widoczne w głównym widoku.
- App: wysyła `deviceCode`/`deviceName`/per-punkt `s`; testy przechodzą; gradle build OK.
- Web: W5 usunięty; telefon widoczny w głównej mapie/historii/liście devices; zrzuty
  (Playwright/VPN) potwierdzają. Push do GitHub po weryfikacji na serwerze.
