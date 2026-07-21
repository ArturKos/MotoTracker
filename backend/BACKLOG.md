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

| #  | Zadanie | Status |
|----|---------|--------|
| BE1 | **Migracja `migrations/007_user_email_and_app_routes.sql`.** `ALTER TABLE users ADD COLUMN email VARCHAR(190) UNIQUE NULL` (nullable — istniejące konta przeżyją; nowe rejestracje ustawiają e-mail). `CREATE TABLE app_routes` (`id, user_id FK→users(id), client_uuid VARCHAR(64), name VARCHAR(190), started_at DATETIME, km DOUBLE, dur_sec INT, avg_kmh DOUBLE, max_kmh DOUBLE, path_json LONGTEXT, payload_json LONGTEXT NOT NULL, created_at, updated_at ON UPDATE`), z `UNIQUE(user_id, client_uuid)` i `INDEX(user_id, started_at)`. Idempotentne (`IF NOT EXISTS`/`ADD COLUMN IF NOT EXISTS` jak w istniejących migracjach). **DoD:** aplikuje się czysto na bazie z istniejącymi danymi; `users.email` i `app_routes` istnieją; FK trzyma. `🔬`→`✅` po odpaleniu na serwerze. | ⬜ |
| BE2 | **`register.php` — otwarta rejestracja (POST).** Przyjmij JSON **lub** form: `email`, `password`, opcjonalnie `display_name`. Walidacja: `filter_var(...FILTER_VALIDATE_EMAIL)`, hasło min. 8 znaków (stała `MIN_PASSWORD_LEN`) → `400 {"error":"invalid_input","detail":"invalid_email|weak_password"}`. Unikalność e-maila/username → `409 {"error":"email_taken"}`. Utwórz usera: `username`=e-mail, `password_hash($pw, PASSWORD_DEFAULT)` (**bcrypt = sól per-hasło wbudowana; NIE dodawać własnej soli**), `write_api_key`+`read_api_key`=`bin2hex(random_bytes(16))`, `is_admin=0, active=1, must_change_password=0`. Prepared statements. Auto-login: `session_regenerate_id(true)` + `$_SESSION['user_id']`. `200 {"ok":true,"user_id":N}`. Metoda≠POST→`405`, błąd DB→`500` (bez wycieku). Wzór: `tools/add_user.php`. **DoD:** nowy e-mail→200+wiersz w DB z bcrypt-hash i kluczami; duplikat→409; złe dane→400. | ⬜ |
| BE3 | **`login.php` — logowanie e-mailem (adaptacja).** Przyjmij `email` **lub** `username`; lookup `WHERE email = ? OR username = ?`. Reszta bez zmian (`password_verify`, `session_regenerate_id(true)`, `$_SESSION['user_id']`, JSON, 401 throttle, 403 `account_disabled`). **Zgodność wstecz:** istniejący web-front (`login.html`, pole `username`) nadal działa. **DoD:** login e-mailem→200+sesja; istniejący user po `username`→200; złe hasło→401. | ⬜ |
| BE4 | **`api_routes.php` — zapis trasy z aplikacji (POST, auth).** `include('auth.php')` + `auth_require_write()` (sesja lub write_api_key → wypełnia `$current_user`; brak→401 `unauthorized`). Metoda≠POST→`405`. Wejście `application/json` (`json_decode(file_get_contents('php://input'))`). Wymagane `id`,`name`,`dateEpochMs`→ brak→`400`. Mapowanie: `client_uuid`=`id`, `started_at`=`date('Y-m-d H:i:s', dateEpochMs/1000)`, `km`, `dur_sec`=`durSec`, `avg_kmh`=`avg`, `max_kmh`=`max`, `path_json`=`pathJson`, `payload_json`=surowe ciało. **Upsert** `INSERT ... ON DUPLICATE KEY UPDATE ...` po `(user_id, client_uuid)` (prepared). `200 {"ok":true,"route_id":N}`. **DoD:** bez sesji→401; z sesją+payload→200+wiersz w `app_routes`; **ten sam `client_uuid` ponownie→200 bez duplikatu**; brak wymaganego pola→400. | ⬜ |
| BE5 | **Scenariusz weryfikacyjny `backend/tests/api_smoke.sh` (curl).** Skrypt: register (200) → register duplikat (409) → register złe hasło (400) → login e-mailem (200, cookie jar) → api_routes bez cookie (401) → api_routes z cookie (200) → api_routes ten sam `client_uuid` (200, brak duplikatu — sprawdź `SELECT COUNT`) → api_routes bez `name` (400). Parametryzowany `BASE_URL` (domyślnie lokalny; docelowo `http://192.168.1.145/gpstrack`). **DoD:** skrypt przechodzi na uruchomionym backendzie → przesuwa BE1–BE4 z `🔬` na `✅`. | ⬜ |

*(Kontrakt dla dalszych podprojektów: app [2] uderza w `register.php`/`login.php`(pole `email`)/`api_routes.php` z `id`=client_uuid, wsadowo po jeździe; web [3] czyta `app_routes` per sesyjny `user_id`, rysuje polilinię z `path_json` + statystyki z `payload_json`.)*

## Podprojekt 3 — Webowy widok „przejazdy z aplikacji" (po backendzie i/lub równolegle)

*(Do rozpisania po BE1–BE5. Szkic: `GET` listujący `app_routes` usera + strona/zakładka
w web-froncie: lista nazwanych przejazdów [data, dystans, avg/max] + szczegół z mapą
[polilinia z `path_json`] i statystykami [`payload_json`]. Branding spójny ze splashem/ikoną.)*
