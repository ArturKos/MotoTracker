# GPStrack backend API — rejestracja + upload tras z aplikacji (projekt)

**Data:** 2026-07-21
**Status:** zatwierdzony
**Podprojekt 1 z 3** (backend → app → web). Definiuje kontrakt API, z którego korzystają podprojekty 2 (aplikacja) i 3 (widok webowy).

## Cel

Trasy nagrane w aplikacji MotoTracker mają docierać do serwera GPStrack i być
własnością zalogowanego użytkownika, a użytkownik ma móc **założyć konto z poziomu
aplikacji**. Ten podprojekt dostarcza wyłącznie **backend PHP**: migrację schematu,
endpoint rejestracji, adaptację logowania (e-mail) i endpoint przyjmujący trasy.
Widok webowy tras i UI aplikacji są osobnymi podprojektami.

## Kontekst (zweryfikowany w kodzie)

- Backend GPStrack = pliki `.php` w `backend/`, MySQL (`mysqli`), sesje PHP.
  **Brak `.htaccess`** → endpointy to realne pliki `.php` (nie `/api/...`).
- `users` (`001`/`002`/`006`): `id, username UNIQUE, password_hash, display_name,
  write_api_key UNIQUE, read_api_key, is_admin, must_change_password, active,
  created_at`. **Brak kolumny `email`.** Konta dotąd tworzone tylko przez
  `tools/bootstrap_admin.php` / `tools/add_user.php` — **brak self-rejestracji**.
- `login.php`: POST `username`+`password` (form/`$_POST`), `password_verify`,
  `session_regenerate_id(true)`, ustawia `$_SESSION['user_id']`, zwraca JSON.
- `auth.php`: `session_start()`; `auth_require_write()` waliduje sesję **lub**
  `write_api_key` i wypełnia globalny `$current_user` (`id`, `is_admin`, …);
  `auth_fail()` zwraca błąd + kończy.
- Hasła: `password_hash()` (bcrypt) — **sól per-hasło wbudowana w hash** (nie
  dodajemy własnej soli). Klucze API: `bin2hex(random_bytes(16))`.
- **Aplikacja** ma martwy klient: `HttpGpStrackClient` POST-uje na nieistniejące
  `/login` i `/api/routes`. Payload trasy (JSON) już zawiera:
  `id` (String, stabilne lokalne id → **client_uuid**), `name`, `dateEpochMs`,
  `bikeId`, `km`, `durSec`, `avg`, `max`, `lean`, `elev`, `fuel`, `wxJson`,
  `pathJson`, `speedJson`, `elevProfileJson`, `notes`.

## Decyzje (zatwierdzone)

- **Tożsamość:** dodać kolumnę `email` do `users`; rejestracja/logowanie e-mailem
  (username pozostaje dla zgodności istniejącego web-frontu).
- **Rejestracja otwarta** (serwer self-hosted, LAN): e-mail + hasło, walidacja siły
  hasła; **bez** weryfikacji e-mailem (brak infry mailowej). Solenie = bcrypt.
- **Model tras:** nowa tabela `app_routes` (osobne, nazwane przejazdy) — pod
  dedykowany widok webowy „przejazdy z aplikacji" (podprojekt 3). NIE dosypujemy do
  punktowej tabeli `points`.
- **Idempotencja:** upload wsadowy po jeździe z kolejki syncu; `UNIQUE(user_id,
  client_uuid)` + upsert → retry nie duplikuje.
- **Wykonanie:** backend robimy **bezpośrednio** (nie androidową pętlą
  `agent_workflow.py`, która obejmuje tylko app).

## Komponenty

### 1. Migracja `backend/migrations/007_user_email_and_app_routes.sql`

```sql
ALTER TABLE users ADD COLUMN email VARCHAR(190) UNIQUE NULL;

CREATE TABLE IF NOT EXISTS app_routes (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT NOT NULL,
    client_uuid   VARCHAR(64) NOT NULL,
    name          VARCHAR(190) NOT NULL,
    started_at    DATETIME NOT NULL,
    km            DOUBLE NOT NULL DEFAULT 0,
    dur_sec       INT NOT NULL DEFAULT 0,
    avg_kmh       DOUBLE NOT NULL DEFAULT 0,
    max_kmh       DOUBLE NOT NULL DEFAULT 0,
    path_json     LONGTEXT,          -- polilinia do rysowania na webie
    payload_json  LONGTEXT NOT NULL, -- pełny JSON z appki (bogate statystyki)
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_routes_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_user_client (user_id, client_uuid),
    INDEX idx_user_started (user_id, started_at)
);
```
`VARCHAR(190)` — bezpieczne pod unikalny indeks przy utf8mb4. `email` nullable, by
istniejące konta przeżyły ALTER; nowe rejestracje zawsze ustawiają e-mail.
`payload_json` trzyma cały wysłany obiekt (web renderuje z niego bogate statystyki,
a listowanie korzysta z wyodrębnionych kolumn).

### 2. `backend/register.php` (POST)

- Wejście: `application/json` **lub** form — `email`, `password`, opcjonalnie
  `display_name`. (App wyśle JSON; obsłużyć oba jak `ingest_traccar.php`.)
- Walidacja: `filter_var($email, FILTER_VALIDATE_EMAIL)`; hasło min. **8 znaków**
  (stała `MIN_PASSWORD_LEN`); przy braku/niepoprawnych → `400 {"error":"invalid_input"}`
  (z polem szczegółu, np. `weak_password`/`invalid_email`).
- Unikalność: sprawdź `email` (i `username`); duplikat → `409 {"error":"email_taken"}`.
- Utworzenie: `username` = e-mail (spójne z „login e-mailem"); `password_hash($pw,
  PASSWORD_DEFAULT)`; `write_api_key`+`read_api_key` = `bin2hex(random_bytes(16))`;
  `is_admin=0`, `active=1`, `must_change_password=0`. INSERT przez prepared statement.
- Auto-login: `session_regenerate_id(true)`, `$_SESSION['user_id']` = nowe id.
- Odpowiedź: `200 {"ok":true,"user_id":N}`. Content-Type `application/json`.
- Metoda ≠ POST → `405`. Błąd DB → `500` (bez wycieku szczegółów). Wzorować się na
  `tools/add_user.php` (tworzenie usera + api_key).

### 3. `backend/login.php` (adaptacja — logowanie e-mailem)

- Przyjmować `email` **lub** `username` (jedno z pól). Lookup:
  `SELECT ... FROM users WHERE email = ? OR username = ?` (bind ta sama wartość do
  obu, albo osobne gałęzie). Reszta bez zmian (`password_verify`,
  `session_regenerate_id`, `$_SESSION['user_id']`, zwrot JSON, throttle 401).
- Zgodność wstecz: istniejący web-front (`login.html`) wysyła `username` — nadal działa.

### 4. `backend/api_routes.php` (POST — zapis trasy)

- `include('auth.php')`; `auth_require_write()` → waliduje sesję (lub write_api_key)
  i wypełnia `$current_user`. Brak auth → `auth.php` zwraca 401
  (`{"error":"unauthorized"}`); app czyści sesję i prosi o re-login.
- Metoda ≠ POST → `405`.
- Wejście: `application/json` (ciało = obiekt trasy z appki). Parsowanie
  `json_decode(file_get_contents('php://input'), true)`.
- Walidacja: wymagane `id` (→`client_uuid`), `name`, `dateEpochMs`. Brak → `400`.
- Mapowanie: `client_uuid`=`id`; `started_at` = `date('Y-m-d H:i:s', dateEpochMs/1000)`;
  `km`,`dur_sec`(=`durSec`),`avg_kmh`(=`avg`),`max_kmh`(=`max`); `path_json`=`pathJson`;
  `payload_json` = całe surowe ciało.
- Upsert po `(user_id, client_uuid)`:
  `INSERT ... ON DUPLICATE KEY UPDATE name=…, started_at=…, km=…, dur_sec=…,
  avg_kmh=…, max_kmh=…, path_json=…, payload_json=…` (prepared statement).
- Odpowiedź: `200 {"ok":true,"route_id":N}`.

## Kontrakt dla podprojektów 2/3

- **App (2):** klient uderza w `register.php` (JSON email/password), `login.php`
  (pole `email`), `api_routes.php` (JSON trasy z `id`=client_uuid); kolejka syncu
  wysyła wsadowo po zakończeniu jazdy; 401 → wyczyść sesję + re-login.
- **Web (3):** czyta `app_routes` per `user_id` (sesja); lista po `started_at`,
  szczegół rysuje polilinię z `path_json` + statystyki z `payload_json`.

## Testy / weryfikacja

Środowisko: lokalny PHP + MySQL (lub serwer GPStrack `192.168.1.145`). Skrypt
`curl`-owy w `backend/tests/` **lub** ręczny scenariusz:
1. Migracja `007` aplikuje się czysto; `users.email` istnieje; `app_routes` istnieje.
2. `register.php` — nowy e-mail → 200 + user w DB z bcrypt-hash i api_key; ten sam
   e-mail ponownie → 409; złe hasło/e-mail → 400.
3. `login.php` — e-mailem 200 + sesja; istniejący user `username` nadal 200; złe hasło 401.
4. `api_routes.php` — bez sesji → 401; z sesją, payload trasy → 200 + wiersz w
   `app_routes`; **ten sam `client_uuid` ponownie → 200, brak duplikatu** (upsert);
   brak wymaganego pola → 400.
5. `password_verify` przechodzi dla zarejestrowanego hasła (potwierdza poprawny hash).

## Poza zakresem (inne podprojekty / później)

- UI rejestracji/logowania i przepięcie uploadu w aplikacji (podprojekt 2).
- Webowy widok „przejazdy z aplikacji" + endpoint listujący `GET` (podprojekt 3).
- Reset/zmiana hasła z aplikacji, weryfikacja e-mail, rate-limiting rejestracji
  (do rozważenia, gdy serwer wyjdzie poza LAN).
- Dosypywanie punktów do `points` / integracja z istniejącą mapą punktową.
