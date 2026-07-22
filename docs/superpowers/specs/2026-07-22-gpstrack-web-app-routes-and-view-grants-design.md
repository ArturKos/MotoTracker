# GPStrack web: widok „przejazdy z aplikacji" + model uprawnień (granty widoczności)

**Data:** 2026-07-22
**Status:** zatwierdzony
**Podprojekt 3 z 3** integracji app↔GPStrack (backend ✅ → app ✅ → **web (ten)**).
Kontekst: [[mototracker-gpstrack-integration]]. Backend BE1–BE6 i app AH/AI gotowe.

## Cel

W webowym GPStrack ma być **widok tras nagranych w aplikacji** (`app_routes`), oraz
**model uprawnień per zasób**: administrator nadaje konkretnemu kontu prawo do
oglądania danych z **wybranych cudzych urządzeń** (punkty) i **wybranych cudzych kont
aplikacji** (trasy z appki). Dziś widoczność jest zero-jedynkowa (własne / admin=wszystko).

## Decyzje (zatwierdzone)

- **Granularność: per zasób** — grant wskazuje konkretne urządzenie ALBO konkretne
  konto-appki.
- Dwa niezależne typy grantu: `device` (punkty danego urządzenia) i `app_user`
  (**tylko trasy z appki** danego użytkownika — NIE jego urządzenia; pełny rozdział).
- Zarządzane w **`admin.html`** (panel admina), admin-only.
- Model uprawnień budujemy **od razu** (widok web od początku świadomy uprawnień).
- Wykonanie **bezpośrednio** na serwerze `malinka` (backend+web poza androidową pętlą),
  jako Artur; push do GitHub **po weryfikacji**.

## Kontekst (zweryfikowany w kodzie/serwerze)

- `admin.html` zarządza **Users + Devices** przez endpointy w `admin/`
  (`list_users.php`, `list_devices.php`, `reset_password.php`, `regen_api_key.php`,
  `regen_view_key.php`, `toggle_user.php`, `reassign_device.php`); admin-only.
- **Widoczność** filtruje `device_user_filter()` (w `auth.php`): nie-admin →
  `" AND d.user_id = <id>"`, admin → `""` (wszystko). Konsumenci: `pobierz_historie.php`,
  `pobierz_punkty.php`, `pobierz_match.php`, `pobierz_ostatni_punkt.php`, `export_gpx.php`.
- `app_routes` (BE1): własność `user_id`, kolumny `client_uuid, name, started_at, km,
  dur_sec, avg_kmh, max_kmh, path_json, payload_json`.
- **Rozjazd serwer↔repo:** `backend/` w repo NIE jest lustrem serwera. Serwer jest DO
  PRZODU: `index.html`+`map.js` (Moving Time/Avg, `map.js?v=8-moving`), `images.html` +
  `get_images.php` + kilka `pobierz_*.php` — brak w repo. Reszta identyczna.

## Komponenty

### 0. Prereq — reconcile serwer → repo

Przed dotknięciem web-frontu ściągnąć z `malinka` pliki nowsze/nieobecne w repo
(`index.html`, `map.js`, `images.html`, `get_images.php`, `pobierz_ostatni_punkt.php`,
`pobierz_punkty.php`, `pobierz_urzadzenia.php`, `pobierz_me.php`, `pobierz_match.php`,
oraz cokolwiek z `admin/` czego brak w repo) i zacommitować jako Artur. Cel: repo =
źródło prawdy, wdrożenie widoku nie nadpisze pracy z serwera.

### 1. Migracja `009_view_grants.sql`

(007 = email+app_routes, 008 może nie istnieć — użyć następnego wolnego numeru;
sprawdzić stan `migrations/` na serwerze przed nadaniem numeru.)

```sql
CREATE TABLE IF NOT EXISTS view_grants (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    grantee_user_id INT NOT NULL,
    resource_type  ENUM('device','app_user') NOT NULL,
    resource_id    INT NOT NULL,
    created_by     INT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vg_grantee FOREIGN KEY (grantee_user_id) REFERENCES users(id),
    UNIQUE KEY uq_grant (grantee_user_id, resource_type, resource_id),
    INDEX idx_grantee (grantee_user_id)
);
```

### 2. Filtry widoczności (`auth.php`)

- **`device_user_filter()`** — rozszerzyć: admin → `""` (bez zmian). Nie-admin →
  `" AND (d.user_id = <id> OR d.id IN (<granted device ids>))"`, gdzie granted =
  `SELECT resource_id FROM view_grants WHERE grantee_user_id=<id> AND resource_type='device'`.
  Gdy brak grantów → jak dziś (`AND d.user_id=<id>`). Read-only token: własne + granty
  (grant per user, nie per token).
- **Nowy `app_routes_user_ids()`** → zwraca listę id: `[<self>] + granty app_user`.
  Helper do zapytań o `app_routes` (SQL `WHERE user_id IN (...)`). Admin → wszyscy
  (albo self+granty; decyzja: admin widzi wszystkie `app_routes`, spójnie z devices).

### 3. Endpoint odczytu tras `pobierz_app_trasy.php` (GET, sesja)

- `include auth.php` (sesja/klucz). Bez `?id=` → **lista**: `SELECT id, user_id, name,
  started_at, km, dur_sec, avg_kmh, max_kmh FROM app_routes WHERE user_id IN (<app_routes_user_ids>)
  ORDER BY started_at DESC`. Z `?id=N` → **szczegół** (dodać `path_json, payload_json`),
  ale tylko jeśli `user_id` wiersza ∈ widocznych (inaczej 404/403).
- Zwraca JSON. Prepared statements.

### 4. Admin UI (`admin.html`) + endpointy `admin/`

- Endpointy (admin-only, wzór istniejących `admin/*.php`):
  - `admin/list_grants.php?grantee=<uid>` → lista grantów konta (+ dane zasobu:
    nazwa urządzenia / e-mail konta-appki).
  - `admin/add_grant.php` (POST `grantee`, `resource_type`, `resource_id`) → INSERT
    (idempotentnie po UNIQUE), walidacja że zasób istnieje.
  - `admin/remove_grant.php` (POST `grant_id` lub trójka) → DELETE.
- `admin.html`: przy koncie (w tabeli Users) akcja „Grants" → modal: lista bieżących
  grantów + wybór urządzenia (z `list_devices`) / konta-appki (z `list_users`) do
  dodania + usuwanie. Styl jak istniejące modale (reset/regen/reassign).

### 5. Widok web „Przejazdy z aplikacji"

- Strona/zakładka (nowy plik, np. `app_routes.html` + JS, LUB sekcja w istniejącym
  `index.html`/nowa karta) czytająca `pobierz_app_trasy.php`:
  - **Lista**: nazwane przejazdy (nazwa, data, dystans, avg/max), z oznaczeniem
    właściciela gdy widać cudze (granty).
  - **Szczegół**: mapa (Leaflet jak `map.js`) rysująca polilinię z `path_json` +
    statystyki z `payload_json` (lean/elev/fuel/speedOverTime, ile się da).
  - Branding spójny z appką (paleta splasha: #101114/#2DD4FF/#00E676).
- Nawigacja: link z głównego web-frontu (i/lub z `index.html`).

## Testy / weryfikacja (na `malinka`)

- Migracja `009` aplikuje się czysto; `view_grants` istnieje.
- `curl`/skrypt: (a) user A widzi tylko swoje `app_routes` przez `pobierz_app_trasy.php`;
  (b) admin dodaje grant `app_user`(A) dla usera B → B widzi trasy A; usunięcie → nie
  widzi; (c) grant `device` → B widzi punkty tego urządzenia w `pobierz_historie.php`,
  bez grantu → nie; (d) endpointy `admin/*grant*` odrzucają nie-admina.
- Widok web: otworzyć w przeglądarce (przez VPN z laptopa) — lista + szczegół z mapą
  renderują; sprawdzić na koncie z grantem i bez. Zrzuty ekranu.
- Cleanup danych testowych po weryfikacji.

## Poza zakresem

- Zmiana modelu punktowego trackerów sprzętowych (tylko rozszerzamy filtr o granty).
- Edycja/kasowanie tras z poziomu web (na razie tylko odczyt).
- Współdzielenie odwrotne (app widzi web) — nie dotyczy.

## Kolejność wdrożenia

0 (reconcile) → 1 (migracja) → 2 (filtry) → 3 (endpoint) → 4 (admin) → 5 (widok). Push
do GitHub **po** weryfikacji na serwerze (zgoda Akosa).
