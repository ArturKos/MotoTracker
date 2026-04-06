#define TINY_GSM_MODEM_SIM7070
#define TINY_GSM_RX_BUFFER 1024
#define SerialAT Serial1

// Uncomment to see AT commands in Serial Monitor
// #define DUMP_AT_COMMANDS

#define TINY_GSM_TEST_GPRS    true
#define TINY_GSM_TEST_GPS     true

// GSM PIN (leave empty if none)
#define GSM_PIN ""

// GPRS credentials
const char apn[]      = "internet";
const char gprsUser[] = "";
const char gprsPass[] = "";

// Hardware pins (LilyGO T-SIM7000G)
#define UART_BAUD   115200
#define PIN_DTR     25
#define PIN_TX      27
#define PIN_RX      26
#define PWR_PIN     4
#define SD_MISO     2
#define SD_MOSI     15
#define SD_SCLK     14
#define SD_CS       13
#define LED_PIN     12
#define ADC_PIN     35

// Server settings (HTTP, no TLS — saves ~3s per request on SIM7000G)
const char IOT_SERVER[] = "YOUR_SERVER_HOSTNAME";
const int  IOT_SERVER_PORT = 80;

// Database credentials
const char* TABLE_NAME    = "S_02";
const char* API_KEY       = "YOUR_API_KEY_HERE";

// GPS coordinate precision (6 = ~0.1m accuracy, more than enough)
#define GPS_PRECISION 6

// ============================================================
// TRACKING MODE — uncomment ONE:
// ============================================================
// LIVE: GPS always on, send every second, no deep sleep (high battery use)
#define TRACKING_MODE_LIVE

// BATCH: deep sleep between fixes, batch N points, low power
// #define TRACKING_MODE_BATCH
// ============================================================

// ---- LIVE mode settings ----
#define LIVE_INTERVAL_MS    1000    // Send interval (ms). 1000 = 1 point/sec
#define LIVE_BATTERY_CHECK  60000   // Check battery every 60 seconds
#define LIVE_MIN_VOLTAGE    3.3     // Auto-stop below this voltage to protect battery
#define LIVE_SD_BATCH       10      // Buffer N points on SD before retrying network

// ---- BATCH mode settings ----
#define uS_TO_S_FACTOR  1000000ULL
#define TIME_TO_SLEEP   60          // Deep sleep interval (seconds)
#define BATCH_SIZE      5           // Points to collect before sending

// ---- SD card offline buffer (both modes) ----
// Each point ~80 bytes CSV. 100000 = ~8MB on a 32GB card
// At 1pt/sec live mode: 100000 pts = ~27 hours offline storage
#define SD_BUFFER_MAX   100000
#define SD_BUFFER_FILE  "/gps_buffer.csv"

// GPS fix timeout (milliseconds) — used in BATCH mode initial fix
#define GPS_TIMEOUT 120000
