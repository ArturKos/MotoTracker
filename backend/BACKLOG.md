# BACKLOG — GPStrack backend (PHP)

> **Zakres i wykonanie.** Ten plik śledzi prace **backendu PHP + web** GPStrack.
> **NIE jest czytany przez `agent_workflow.py`** (androidowa pętla, której architekt
> czerpie z `Android/.../BACKLOG.md`, a tester weryfikuje gradlem — nie zbuduje ani
> nie przetestuje PHP). Zadania stąd realizujemy **bezpośrednio** (ręcznie /
> subagentami), nie pętlą.
>
> Legenda statusów: `⬜` todo · `✅` zrobione i zweryfikowane · `🔬` gotowe w kodzie,
> czeka na weryfikację na działającym serwerze (PHP+MySQL).
>
> **Definition of Done (backend):** endpoint/migracja działa na lokalnym PHP+MySQL
> lub serwerze GPStrack (`192.168.1.145`); prepared statements (bez SQL-injection);
> spójne kody HTTP + JSON; scenariusz weryfikacyjny (`curl`) przechodzi. Brak
> unit-test-harnessu PHP w repo → weryfikacja skryptem `curl`/ręczna = `🔬` do czasu
> potwierdzenia na serwerze.

---

## Podprojekt 1 — API: rejestracja + upload tras z aplikacji

Projekt: `docs/superpowers/specs/2026-07-21-gpstrack-backend-api-design.md` (pełny
kontrakt, mapowania pól, decyzje). Kolejność w ramach całości: **backend (tu) → app
(androidowy BACKLOG, podprojekt 2) → web (podprojekt 3)**.

**BE1–BE5 ZAIMPLEMENTOWANE I ZWERYFIKOWANE NA SERWERZE 192.168.1.145 (`ssh malinka`) 2026-07-21.**
Wdrożone do `/var/www/html/gpstrack/` (pliki kopiowane, nie git; `migrations/` i `tests/`
wymagają `sudo`, katalog główny zapisywalny). Migracja 007 zaaplikowana (`users.email`✓,
`app_routes`✓). Smoke-test `tests/api_smoke.sh` (localhost) = **ALL PASS** (register
200/409/400, login e-mailem 200, api_routes 401/200/upsert-ten-sam-route_id/400). Zapis
w DB potwierdzony (upsert nadpisuje, `payload_json` zachowany). Dane testowe sprzątnięte.
Backup: `login.php.bak-preemail` na serwerze.

| #  | Zadanie | Status |
|----|---------|--------|
| BE1 | **Migracja `migrations/007_user_email_and_app_routes.sql`.** `ALTER TABLE users ADD COLUMN email VARCHAR(190) UNIQUE NULL` (nullable — istniejące konta przeżyją; nowe rejestracje ustawiają e-mail). `CREATE TABLE app_routes` (`id, user_id FK→users(id), client_uuid VARCHAR(64), name VARCHAR(190), started_at DATETIME, km DOUBLE, dur_sec INT, avg_kmh DOUBLE, max_kmh DOUBLE, path_json LONGTEXT, payload_json LONGTEXT NOT NULL, created_at, updated_at ON UPDATE`), z `UNIQUE(user_id, client_uuid)` i `INDEX(user_id, started_at)`. Idempotentne (`IF NOT EXISTS`/`ADD COLUMN IF NOT EXISTS` jak w istniejących migracjach). **DoD:** aplikuje się czysto na bazie z istniejącymi danymi; `users.email` i `app_routes` istnieją; FK trzyma. `🔬`→`✅` po odpaleniu na serwerze. | ✅ |
| BE2 | **`register.php` — otwarta rejestracja (POST).** Przyjmij JSON **lub** form: `email`, `password`, opcjonalnie `display_name`. Walidacja: `filter_var(...FILTER_VALIDATE_EMAIL)`, hasło min. 8 znaków (stała `MIN_PASSWORD_LEN`) → `400 {"error":"invalid_input","detail":"invalid_email|weak_password"}`. Unikalność e-maila/username → `409 {"error":"email_taken"}`. Utwórz usera: `username`=e-mail, `password_hash($pw, PASSWORD_DEFAULT)` (**bcrypt = sól per-hasło wbudowana; NIE dodawać własnej soli**), `write_api_key`+`read_api_key`=`bin2hex(random_bytes(16))`, `is_admin=0, active=1, must_change_password=0`. Prepared statements. Auto-login: `session_regenerate_id(true)` + `$_SESSION['user_id']`. `200 {"ok":true,"user_id":N}`. Metoda≠POST→`405`, błąd DB→`500` (bez wycieku). Wzór: `tools/add_user.php`. **DoD:** nowy e-mail→200+wiersz w DB z bcrypt-hash i kluczami; duplikat→409; złe dane→400. | ✅ |
| BE3 | **`login.php` — logowanie e-mailem (adaptacja).** Przyjmij `email` **lub** `username`; lookup `WHERE email = ? OR username = ?`. Reszta bez zmian (`password_verify`, `session_regenerate_id(true)`, `$_SESSION['user_id']`, JSON, 401 throttle, 403 `account_disabled`). **Zgodność wstecz:** istniejący web-front (`login.html`, pole `username`) nadal działa. **DoD:** login e-mailem→200+sesja; istniejący user po `username`→200; złe hasło→401. | ✅ |
| BE4 | **`api_routes.php` — zapis trasy z aplikacji (POST, auth).** `include('auth.php')` + `auth_require_write()` (sesja lub write_api_key → wypełnia `$current_user`; brak→401 `unauthorized`). Metoda≠POST→`405`. Wejście `application/json` (`json_decode(file_get_contents('php://input'))`). Wymagane `id`,`name`,`dateEpochMs`→ brak→`400`. Mapowanie: `client_uuid`=`id`, `started_at`=`date('Y-m-d H:i:s', dateEpochMs/1000)`, `km`, `dur_sec`=`durSec`, `avg_kmh`=`avg`, `max_kmh`=`max`, `path_json`=`pathJson`, `payload_json`=surowe ciało. **Upsert** `INSERT ... ON DUPLICATE KEY UPDATE ...` po `(user_id, client_uuid)` (prepared). `200 {"ok":true,"route_id":N}`. **DoD:** bez sesji→401; z sesją+payload→200+wiersz w `app_routes`; **ten sam `client_uuid` ponownie→200 bez duplikatu**; brak wymaganego pola→400. | ✅ |
| BE6 | **Write przez `write_api_key` (Bearer) — sync w tle bez wygasającej sesji.** ✅ zweryfikowane na serwerze 2026-07-21. `auth.php`: ścieżka tokenowa próbuje najpierw `users.write_api_key` → `$current_user` + `$auth_readonly=false` (pełny write), fallback na `read_api_key` (read-only jak dotąd). `register.php` i `login.php` zwracają `write_api_key` w JSON. Efekt: app zapisuje klucz raz i uwierzytelnia upload nagłówkiem `Authorization: Bearer <write_api_key>` — bez cookie/sesji. **UWAGA:** LAN/http = klucz plaintextem (akceptowalne dla self-hosted LAN). Smoke-test rozszerzony: register zwraca klucz; `api_routes.php` przez Bearer bez cookie→200; bogus token→401; sesja nadal działa. | ✅ |
| BE5 | **Scenariusz weryfikacyjny `backend/tests/api_smoke.sh` (curl).** Skrypt: register (200) → register duplikat (409) → register złe hasło (400) → login e-mailem (200, cookie jar) → api_routes bez cookie (401) → api_routes z cookie (200) → api_routes ten sam `client_uuid` (200, brak duplikatu — sprawdź `SELECT COUNT`) → api_routes bez `name` (400). Parametryzowany `BASE_URL` (domyślnie lokalny; docelowo `http://192.168.1.145/gpstrack`). **DoD:** skrypt przechodzi na uruchomionym backendzie → przesuwa BE1–BE4 z `🔬` na `✅`. | ✅ |

*(Kontrakt dla dalszych podprojektów: app [2] uderza w `register.php`/`login.php`(pole `email`)/`api_routes.php` z `id`=client_uuid, wsadowo po jeździe; web [3] czyta `app_routes` per sesyjny `user_id`, rysuje polilinię z `path_json` + statystyki z `payload_json`.)*

## Podprojekt 3 — Webowy widok „przejazdy z aplikacji" + model uprawnień (granty per zasób)

Projekt: `docs/superpowers/specs/2026-07-22-gpstrack-web-app-routes-and-view-grants-design.md`.
Decyzje: grant **per zasób** (typ `device` = punkty urządzenia; typ `app_user` = TYLKO
trasy z appki danego usera), zarządzane w `admin.html` (admin-only), model uprawnień
od razu. Wykonanie bezpośrednio na `malinka`, jako Artur; **push do GitHub PO weryfikacji**.

| #  | Zadanie | Status |
|----|---------|--------|
| W0 | **Reconcile serwer→repo (prereq).** Ściągnąć z `malinka` pliki nowsze/nieobecne w repo `backend/`: `index.html`, `map.js` (Moving Time/Avg, `v=8-moving`), `images.html`, `get_images.php`, oraz brakujące `pobierz_*.php` (`pobierz_ostatni_punkt.php`, `pobierz_punkty.php`, `pobierz_urzadzenia.php`, `pobierz_me.php`, `pobierz_match.php`) i cokolwiek z `admin/` czego brak w repo. Zacommitować (Artur) OSOBNYM commitem „sync z serwera". **DoD:** `diff` repo↔serwer dla web/PHP = brak istotnych różnic (poza tym co dopiero dodamy). | ✅ |
| W1 | **Migracja `view_grants` (następny wolny numer, sprawdzić `migrations/` na serwerze — prawdop. `008`/`009`).** `CREATE TABLE view_grants (id, grantee_user_id INT FK→users(id), resource_type ENUM('device','app_user'), resource_id INT, created_by INT NULL, created_at, UNIQUE(grantee_user_id,resource_type,resource_id), INDEX(grantee_user_id))`. Idempotentne. **DoD:** aplikuje się czysto; tabela istnieje. | ✅ |
| W2 | **Filtry widoczności w `auth.php`.** Rozszerzyć `device_user_filter()`: admin→`""`; nie-admin→`" AND (d.user_id = <id> OR d.id IN (SELECT resource_id FROM view_grants WHERE grantee_user_id=<id> AND resource_type='device'))"`. Dodać helper `app_routes_user_ids()` → lista `[self] + (granty app_user)`; admin→wszyscy userzy (spójnie z devices). Prepared/escaped (id to int z sesji — bezpieczne). **DoD:** istniejące `pobierz_*`/`export_gpx` działają jak dotąd bez grantów; z grantem `device` widać cudze urządzenie. | ✅ |
| W3 | **`pobierz_app_trasy.php` (GET, sesja).** Bez `?id` → lista `app_routes` `WHERE user_id IN (app_routes_user_ids())` (id, user_id, name, started_at, km, dur_sec, avg_kmh, max_kmh) `ORDER BY started_at DESC`. Z `?id=N` → szczegół (+`path_json`,`payload_json`) tylko gdy `user_id` wiersza ∈ widocznych, inaczej 404. Prepared statements, JSON. **DoD:** user widzi swoje; z grantem `app_user` widzi cudze; bez — nie (404 na szczegół, brak na liście). | ✅ |
| W4 | **Admin UI grantów: `admin/list_grants.php` / `add_grant.php` / `remove_grant.php` + sekcja w `admin.html`.** Endpointy admin-only (wzór istniejących `admin/*.php`): list (po `grantee`, z nazwą urządzenia/e-mailem konta), add (POST grantee+resource_type+resource_id, idempotentnie, walidacja istnienia zasobu), remove (POST grant_id). `admin.html`: akcja „Grants" przy koncie → modal (bieżące granty + dodaj urządzenie z `list_devices`/konto-appki z `list_users` + usuń), styl jak reset/regen/reassign. **DoD:** admin nadaje/usuwa granty w UI; nie-admin dostaje 401/403 na `admin/*grant*`. | ⬜ |
| W5 | **Widok web „Przejazdy z aplikacji".** Strona/zakładka (`app_routes.html`+JS lub karta w istniejącym froncie) czytająca `pobierz_app_trasy.php`: lista nazwanych przejazdów (nazwa, data, dystans, avg/max; oznaczenie właściciela gdy widać cudze) + szczegół z mapą Leaflet (polilinia z `path_json`) i statystykami z `payload_json`. Branding: paleta splasha (#101114/#2DD4FF/#00E676). Link z głównego frontu. **DoD:** w przeglądarce (VPN z laptopa) lista+szczegół+mapa renderują; sprawdzone na koncie z grantem i bez; zrzuty. | ⬜ |

*(Kolejność: W0→W1→W2→W3→W4→W5. Po weryfikacji na serwerze — push repo do GitHub jako Artur.)*

**STAN 2026-07-22:** W0–W3 ✅ + **W4 endpointy backendu (`admin/list_grants|add_grant|remove_grant.php`) ✅ zweryfikowane** na serwerze (`admin_grant_verify.sh`: 403 nie-admin, add/list/idempotent/404/remove, grant realnie steruje widocznością — ALL PASS). Migracja `008_view_grants.sql` zaaplikowana. Filtry w `auth.php` (`device_user_filter` rozszerzony + `app_routes_user_ids()`/`grant_resource_ids()`), `pobierz_app_trasy.php` — wdrożone i zweryfikowane (`grant_verify.sh` ALL PASS). ZOSTAJE **W4-UI** (modal grantów w `admin.html`) i **W5** (widok web „przejazdy z aplikacji"). Backend+API+uprawnienia wypushowane; UI do zrobienia.
