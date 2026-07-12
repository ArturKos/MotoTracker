[![CI](https://github.com/ArturKos/MotoTracker/actions/workflows/ci.yml/badge.svg)](https://github.com/ArturKos/MotoTracker/actions/workflows/ci.yml)

# Handoff: MotoTracker — aplikacja Android do logowania tras motocyklowych

## Overview
MotoTracker to aplikacja mobilna (Android) do rejestrowania i przeglądania tras motocyklowych — odpowiednik funkcjonalny Yamaha MyRide. Nagrywa przejazd (GPS, prędkość, dystans, pochylenie, wysokość, pogoda), zapisuje trasy lokalnie **oraz** synchronizuje je z istniejącym serwerem użytkownika (`http://192.168.1.145/gpstrack`). Kluczowe założenie: **aplikacja musi w pełni działać offline** — bez logowania i bez serwera dane zapisują się lokalnie na telefonie i czekają w kolejce na wysłanie.

Pakiet zawiera dwa widoki:
1. **MotoTracker.dc.html** — pełna aplikacja telefoniczna (pionowa, ramka Androida ~412×892), wszystkie ekrany i przepływy.
2. **MotoTracker Android Auto.dc.html** — widok head-unitu samochodowego (poziomy, 1280×720), uproszczony ekran nagrywania „glanceable".

## About the Design Files
Pliki w tym pakiecie to **referencje projektowe wykonane w HTML** — prototypy pokazujące docelowy wygląd i zachowanie, **nie** kod produkcyjny do bezpośredniego skopiowania. Zostały zbudowane w autorskim, lekkim runtime „Design Component" (`.dc.html` + `support.js`) — traktuj je jako specyfikację wizualną i behawioralną, nie jako framework do przeniesienia.

Zadanie polega na **odtworzeniu tych projektów w docelowym środowisku aplikacji**. Ponieważ celem jest natywna aplikacja Android, rekomendowany stos to **Kotlin + Jetpack Compose** (lub Flutter, jeśli zespół preferuje cross-platform). Mapy: **MapLibre / Mapbox** z obsługą offline (kafelki pobierane na urządzenie), albo **osmdroid** dla pełnego OSM offline. Logika nagrywania działa jako *foreground service* z `FusedLocationProviderClient`. Jeśli docelowo powstaje też wersja web spójna z istniejącą stroną `gpstrack/index.html`, te same układy dają się odtworzyć w React/Vue.

## Fidelity
**High-fidelity (hifi).** Kolory, typografia, odstępy, ikony i interakcje są finalne. Odtwórz UI możliwie wiernie, używając komponentów i wzorców docelowego frameworka. Wartości poniżej (hex, rozmiary, wagi) są wiążące.

---

## Design Tokens

### Motyw główny (cockpit — ciemny, domyślny)
| Token | Wartość | Zastosowanie |
|---|---|---|
| `bg` | `#0a0b0d` | tło ekranu |
| `panel` | `#14171b` | karty, paski, pola |
| `panel2` | `#1d2127` | tła zagłębione (paski postępu, awatary) |
| `line` | `rgba(255,255,255,0.08)` | obramowania, separatory |
| `text` | `#f1f4f3` | tekst główny |
| `dim` | `#899089` | tekst drugorzędny, etykiety |
| `accent` | `#00d1b2` (teal) | akcent podstawowy, prędkość, CTA |
| `accent2` | `#ff5c38` (pomarańcz) | akcent ostrzegawczy: stop, pochylenie, offline |
| `radius` | `16px` | promień kart |

### Warianty motywu (przełącznik w Ustawieniach / prop `theme`)
- **grid** — techniczna siatka: `bg #0b0f15`, `panel #121924`, `accent #12d6c0`, `accent2 #ffb020`, `radius 5px`, tło z linii siatki `rgba(130,170,210,0.10)`.
- **light** — dzienny: `bg #e9eeec`, `panel #ffffff`, `text #111819`, `dim #5c6660`, `accent #009a89`, `accent2 #e2542e`, `radius 14px`.

### Kolory akcentu (prop `accent`, wybór użytkownika)
`#00d1b2` (teal, domyślny) · `#ff5c38` / `#c6ff00` / `#3b82f6` jako alternatywy. Tekst na przyciskach akcentu: `#05100e` (prawie czarny). Tekst na przycisku `accent2`: `#1a0a05`.

### Typografia
- **Barlow** — tekst UI, etykiety, treść (400/500/600/700).
- **Barlow Semi Condensed** — nagłówki, tytuły ekranów, tytuły tras, etykiety przycisków (600/700, zwykle `text-transform: uppercase`, `letter-spacing: .3–.5px`).
- **JetBrains Mono** — wszystkie dane liczbowe (prędkość, czas, dystans, współrzędne, daty techniczne) (500/700).

Skala (telefon): licznik czasu 52px, duże liczby kart 30px, prędkość na nagrywaniu 40px, tytuły ekranów 22px, tytuły tras 18px, treść 14–15px, etykiety 10–11px uppercase.
Skala (Android Auto): prędkość 96px, czas/dystans 34px, pochylenie/wysokość 26px, etykiety 12–13px.

### Spacing
Bazowy rytm 10 / 12 / 14 / 16 / 18px. Padding kart 12–16px (telefon), 16–24px (AA). Gap w siatkach 10–14px. Padding ekranu: 16px po bokach.

### Ikonografia
Wszystkie ikony to inline SVG, stroke `currentColor`, `stroke-width: 2`, `stroke-linecap/linejoin: round`, viewBox `0 0 24 24`. Brak biblioteki zewnętrznej — odtwórz równoważnymi ikonami (np. Material Symbols Rounded) zachowując znaczenie.

---

## Screens / Views — aplikacja telefoniczna (MotoTracker.dc.html)

Stała rama: pasek stanu OS (34px) → pasek aplikacji (tytuł + chip synchronizacji) → obszar ekranu (scroll) → dolna nawigacja (5 zakładek) → pill gestu. Pasek aplikacji i nawigacja ukryte na ekranie logowania; nawigacja ukryta na ekranie szczegółów trasy (jest tam strzałka wstecz).

### 1. Logowanie
- **Cel**: zalogowanie do serwera GPStrack **lub** wejście bez konta.
- **Layout**: logo (92×92 karta z sygnetem), nazwa „MotoTracker" (34px uppercase) + podtytuł; niżej pola: Adres serwera (domyślnie `http://192.168.1.145/gpstrack`, mono), E-mail, Hasło.
- **Akcje**: przycisk pełny „Zaloguj i synchronizuj" (`accent`), przycisk obrysowany „Kontynuuj bez logowania", podpis „Bez konta dane zapisują się lokalnie na telefonie." → `skipLogin` ustawia tryb gościa i przechodzi do nagrywania.

### 2. Nagrywanie (główny ekran)
- **Cel**: rejestracja przejazdu na żywo.
- **Layout**: mapa 218px (tło siatki, ślad trasy rosnący animacją `stroke-dashoffset`, pulsujący znacznik pozycji), pod nią dane.
- **Nakładki na mapie**: chip GPS lewy-górny („GPS · 12 sat", gdy `gpsCorrect` też „· na drodze"); chip pogody prawy-górny (temperatura° · wilgotność%, ikona `accent2`); róża wiatrów prawy-dolny (58px, obracana wg kursu, litera kierunku).
- **Dane**: górny rząd Prędkość (kafelek, liczba 40px `accent` + mini-pasek postępu do 160 km/h) | Czas (30px). Rząd 3 kafelków: Dystans | Wysokość | Paliwo. Rząd 2 kafelków: Pochylenie (liczba + obracający się wskaźnik ±) | Kompas (kierunek + stopnie + strzałka).
- **Sterowanie**: stan bezczynny → przycisk „Rozpocznij trasę" (`accent`). Stan nagrywania → „Pauza" (obrys) + „Zakończ" (`accent2`); w pauzie „Wznów" (`accent`) + „Zakończ". Pod przyciskami podpis: „Pauza także przyciskiem na kierownicy".
- **Zachowanie**: timer 1 s; prędkość symulowana sinusoidą, dystans całkowany z prędkości, pochylenie/wysokość/paliwo pochodne; pogoda odświeżana. „Zakończ" tworzy nową trasę (patrz State) i przechodzi do jej szczegółów z toastem („Trasa zapisana" / „Zapisano lokalnie (offline)").

### 3. Trasy (lista)
- **Cel**: przegląd zapisanych przejazdów.
- **Layout**: 2 kafelki podsumowania (liczba tras, suma km) + przycisk importu GPX (ikona). Lista kart: miniatura śladu (76×76 SVG), nazwa, data · przypisany motocykl (+ tag „Sprzedany" jeśli dotyczy), rząd danych mono (dystans, czas, max), znacznik synchronizacji (✓ zsync. / „KOLEJKA" gdy oczekuje).
- **Akcja**: tap karty → szczegóły trasy.

### 4. Szczegóły trasy
- **Cel**: pełny podgląd zakończonego przejazdu.
- **Layout**: mapa 200px z narysowanym śladem (start `accent`, koniec `accent2`); tytuł + data · motocykl (+ ew. „Sprzedany"); siatka 6 kafelków (Dystans, Czas, Śr. pręd., Maks., Pochyl. maks., Paliwo); **karta Pogoda** (temp/wilgotność/opady — lub „offline" gdy zapisana bez internetu); **wykres prędkości** (SVG polyline + wypełnienie); **wykres profilu wysokości**; **karta Spotkania na trasie** (pomachania BT: awatar, ksywa · motocykl, „przy {miejsce} · godzina", lub „Brak spotkań"); przyciski „Eksport / udostępnij" i (gdy w kolejce) „Wyślij".

### 5. Znajomi (grupa i spotkania)
- **Cel**: jazda ze znajomymi i logowanie spotkań.
- **Sekcje**:
  - **Grupa znajomych** — lista członków (awatar z inicjałem, imię, telefon · motocykl) + „Dodaj przez numer telefonu".
  - **Na żywo** (feed) — zdarzenia: „rozpoczął trasę" / „zakończył trasę" / „prędkość maks. {wartość}"; kolor kropki wg typu. **Wymaga internetu** — w trybie bez internetu sekcja pokazuje komunikat „Wymaga internetu".
  - **Pomachania (Bluetooth)** — spotkani riderzy (działa offline przez BT): ksywa · motocykl, „przy {miejsce} · godzina".

### 6. Statystyki
- **Cel**: podsumowania ogólne.
- **Layout**: 4 kafelki (Łączny dystans, Czas w siodle, Przejazdy, Rekord prędkości); wykres słupkowy „Dystans / miesiąc"; karta „Podsumowanie stylu jazdy" (średni kąt pochylenia, średnia prędkość, suma przewyższeń — paski postępu).

### 7. Ustawienia
Kolejne sekcje (nagłówki uppercase `dim`):
- **Konto** — awatar + nazwa (Jan Kowalski / „Tryb gościa"), przycisk Wyloguj / Zaloguj.
- **Moje motocykle** — lista pojazdów: ikona, nazwa, rok · nr rejestracyjny, badge statusu (Aktywny `accent` / Sprzedany `dim`), tag „AKTUALNY" na wybranym; tap wybiera aktualny motocykl; „+ Dodaj motocykl".
- **Wygląd i język** — motyw (cockpit/grid/light), kolor akcentu, język (PL/EN/DE/FR/CS/RU), jednostki (metryczne/imperialne).
- **Serwer i synchronizacja** — adres serwera; przełącznik „Tryb offline" (zapisuj tylko lokalnie); „Auto-synchronizacja".
- **Kolejka synchronizacji** — trasy oczekujące (nazwa, rozmiar, data, „Wyślij"); „Wyślij wszystko"; gdy pusto: „Wszystko zsynchronizowane ✓".
- **Rozgłaszanie (Bluetooth)** — profil danych wysyłanych mijanym riderom: Nazwa/ksywa, Telefon, Skąd jadę, Motocykl (auto), Dziś przejechane (auto), Suma w apce (auto), Media społecznościowe; przycisk zapisu.
- **System i prywatność** — „Praca bez internetu" (wyłącza pogodę i grupę online), „Korekcja GPS do drogi" (map-matching), „Android Auto".
- **Preferencje** — jednostki, autopauza na postoju, ekran zawsze włączony.
- Stopka wersji.

### Wspólne elementy
- **Chip synchronizacji** (pasek aplikacji, każdy ekran): OFFLINE (`accent2`) / „{n} W KOLEJCE" (`accent`, migający) / „SYNC ✓" (neutralny).
- **Dolna nawigacja** (5): Nagrywaj · Trasy · Znajomi · Statystyki · Ustawienia; aktywna zakładka w `accent`.
- **Arkusz eksportu** (bottom sheet): Eksportuj GPX · Udostępnij trasę (link) · Wyślij na serwer GPStrack.
- **Toast**: potwierdzenia akcji, u dołu, auto-znika po ~2,2 s.

---

## Screens / Views — Android Auto (MotoTracker Android Auto.dc.html)

- **Cel**: bezpieczny, „glanceable" ekran nagrywania na wyświetlaczu samochodu (poziomy 1280×720).
- **Layout**: lewy pasek launchera (88px: ikona apki, aktywny kafelek REC, mapy, trasy, ustawienia) → górny pasek statusu (motocykl, BT, „Android Auto", temperatura, zegar) → treść: **mapa** (≈60% szerokości) + **kolumna danych** (430px).
- **Mapa**: ślad trasy, pozycja z pulsem, etykieta obszaru, chip pogody, róża wiatrów, znacznik „na drodze".
- **Kolumna danych**: ogromna prędkość (96px `accent` + pasek), Czas/Dystans, Pochylenie/Wysokość; wielkie przyciski (96px wys.) Start → Pauza/Stop.
- **Motywy**: night (domyślny) / day; kolor akcentu jak w apce.
- Wszystkie cele dotykowe powiększone (min. ~64px) zgodnie z wymogami Android Auto (Car App Library / templates).

---

## Interactions & Behavior
- **Nawigacja ekranów**: stan `screen` (login/record/routes/detail/stats/riders/settings); dolne zakładki i strzałka wstecz na szczegółach.
- **Nagrywanie**: `startRec` zeruje i uruchamia; `pauseRec`/`resumeRec`; `stopRec` finalizuje trasę. Interwał 1 s aktualizuje wszystkie metryki. Ślad trasy animowany (`stroke-dashoffset`), wskaźnik pochylenia i róża wiatrów animowane `transform` z `transition .5–.6s`.
- **Tryby offline** (dwie odrębne rzeczy):
  1. **Tryb offline** (przełącznik) — nagrania zapisują się lokalnie i trafiają do kolejki synchronizacji zamiast na serwer.
  2. **Praca bez internetu** (System) — twardsze: wyłącza funkcje wymagające sieci (pogoda → „offline"; feed grupy → „Wymaga internetu"); pomachania BT działają dalej.
- **Sugerowana nazwa trasy**: przy zakończeniu generowana z obszaru mapy + pory dnia (poranek/popołudnie/wieczór/noc) — np. „Beskid Sądecki — wieczór".
- **Jednostki**: metryczne↔imperialne przeliczają wszystkie prędkości/dystanse i etykiety (km/mi, km/h/mph).
- **i18n**: pełne tłumaczenie UI w 6 językach (PL/EN/DE/FR/CS/RU); zmiana natychmiastowa.
- **Toasty**: eksport GPX, udostępnienie (link skopiowany), wysyłka na serwer, import GPX, dodanie motocykla/członka, zapis profilu rozgłaszania.

## State Management
Stan aplikacji (odtworzyć w ViewModel / store):
- `screen`, `authed`, `lang`, `theme`, `accent`, `units`.
- Nagrywanie: `recording`, `paused`, `t` (s), `dist`, `spd`, `lean`, `elev`, `fuel`, `weather{temp,hum,rain}`.
- `offline` (przełącznik trybu), `autoSync`, `offlineOnly` (praca bez internetu), `gpsCorrect` (map-matching).
- `currentBikeId`, `bikes[]` (id, name, year, plate, status: active|sold).
- `routes[]` (id, name, date, weather, km, dur, avg, max, lean, elev, fuel, synced, bikeId, wx{}, meetings[], path, speed, elev-profile).
- `group[]`, `feed[]`, `waves[]`.
- `selId` (wybrana trasa), `showExport`, `toast`.

**Wymagania danych w realnej appce**: GPS (`FusedLocationProvider`) jako foreground service; akcelerometr/żyroskop do kąta pochylenia; API pogodowe wg pozycji (np. Open-Meteo — darmowe) z cache offline; **korekcja GPS (map-matching)** do przyciągania punktów do drogi (np. Valhalla/OSRM `match`, lub offline na pobranych danych OSM — patrz Uwagi); parser/generator **GPX** (import i eksport); klient HTTP do serwera GPStrack + lokalna baza (Room/SQLite) i kolejka wysyłkowa z ponawianiem; BLE advertising/scanning do „pomachań"; SMS/deep-link do zapraszania grupy.

## Assets
- **icon.svg** — ikona/logo aplikacji (sygnet: trasa z punktem start/end w kolorze `accent` na ciemnym tle). Dołączona w pakiecie; użyj do launcher-icon (wygeneruj warianty adaptive-icon).
- Brak zewnętrznych obrazów — mapy w prototypie są schematyczne (SVG). W produkcji podłącz prawdziwe kafelki map (z obsługą offline).
- Ikony UI: inline SVG w plikach — odtwórz zestawem ikon docelowej platformy.
- Czcionki: Barlow, Barlow Semi Condensed, JetBrains Mono (Google Fonts) — dołącz jako zasoby aplikacji.

## Files
- `MotoTracker.dc.html` — pełna aplikacja telefoniczna (wszystkie ekrany). Logika w klasie `Component` (stan + `renderVals()`), szablon w znacznikach — czytaj jako specyfikację, nie kod docelowy.
- `MotoTracker Android Auto.dc.html` — widok Android Auto.
- `icon.svg` — logo/ikona.
- `support.js` — runtime prototypu (tylko do podglądu plików `.dc.html` w przeglądarce; **nie** przenosić do produkcji).

Aby podejrzeć prototypy: otwórz pliki `.dc.html` w przeglądarce (wymagają `support.js` obok). Uwaga: nazwy tłumaczeń i pełne wartości pól znajdziesz w słownikach `TRANS`/`T2` w logice `MotoTracker.dc.html`.

## Screenshots
Folder `screenshots/` — poglądowe zrzuty wszystkich widoków (renderowane z prototypów; drobne artefakty rasteryzacji, np. cienkie słupki wykresu, nie występują w żywej aplikacji):

Aplikacja telefoniczna:
- `phone-01-login.png` — logowanie / wejście bez konta
- `phone-02-record.png` — nagrywanie (stan aktywny)
- `phone-03-routes.png` — lista tras
- `phone-04-detail-top.png` — szczegóły trasy (góra: mapa, statystyki, pogoda)
- `phone-05-detail-bottom.png` — szczegóły trasy (dół: wykresy prędkości i wysokości, spotkania, eksport)
- `phone-06-riders.png` — Znajomi (grupa, feed, pomachania BT)
- `phone-07-stats.png` — statystyki ogólne
- `phone-08-settings-top.png` — ustawienia: konto, motocykle, język, motyw
- `phone-09-settings-mid.png` — ustawienia: kolor akcentu, serwer, tryb offline, kolejka sync
- `phone-10-settings-bottom.png` — ustawienia: preferencje, profil rozgłaszania BT
- `phone-11-settings-system.png` — ustawienia: system i prywatność (praca bez internetu, korekcja GPS, Android Auto)

Android Auto:
- `android-auto-01-idle.png` — ekran bezczynny
- `android-auto-02-recording.png` — nagrywanie w toku

## Uwaga architektoniczna — korekcja GPS
Surowe dane GPS zawierają odchyłki; trasa powinna być „przyciągana" do drogi (map-matching), aby motocykl nie „skakał" obok jezdni. Do pełnego działania offline rozważ silnik map-matchingu na pobranych danych OSM (np. Valhalla z lokalnymi kafelkami, GraphHopper offline) — w prototypie reprezentowane jako opcja „Korekcja GPS do drogi" i znacznik „na drodze".
