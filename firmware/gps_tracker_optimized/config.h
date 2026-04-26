#define TINY_GSM_MODEM_SIM7070
#define TINY_GSM_RX_BUFFER 1024
#define SerialAT Serial1

// Uncomment to see AT commands in Serial Monitor
// #define DUMP_AT_COMMANDS

#define TINY_GSM_TEST_GPRS    true
#define TINY_GSM_TEST_GPS     true

// GSM PIN (leave empty if none)
#define GSM_PIN ""

// ---- Local overrides (credentials, test targets) — gitignored ----
// Copy config_local.h.example to config_local.h and fill in your values.
#if __has_include("config_local.h")
  #include "config_local.h"
#endif

// GPRS credentials (override in config_local.h)
#ifndef APN
  #define APN        "internet"
#endif
#ifndef GPRS_USER
  #define GPRS_USER  ""
#endif
#ifndef GPRS_PASS
  #define GPRS_PASS  ""
#endif

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

// Server settings (override in config_local.h)
#ifndef IOT_SERVER
  #define IOT_SERVER      "YOUR_SERVER_HOSTNAME"
#endif
#ifndef IOT_SERVER_PORT
  #define IOT_SERVER_PORT 80
#endif

// Backend credentials (override in config_local.h)
#ifndef TABLE_NAME
  #define TABLE_NAME  "S_02"
#endif
#ifndef API_KEY
  #define API_KEY     "YOUR_API_KEY_HERE"
#endif

// GPS coordinate precision (6 = ~0.1m accuracy, more than enough)
#define GPS_PRECISION 6

// ============================================================
// TRACKING MODE — uncomment ONE:
// ============================================================
// LIVE: GPS always on, send every second, no deep sleep (high battery use)
// #define TRACKING_MODE_LIVE

// BATCH: deep sleep between fixes, batch N points, low power
#define TRACKING_MODE_BATCH
// ============================================================

// ---- LIVE mode settings ----
#define LIVE_INTERVAL_MS          5000    // Send interval (ms). 5000 = 1 point / 5 s.
                                          // 2G/GSM TCP chokes at higher rates; matches the
                                          // known-working reference firmware.
#define LIVE_BATTERY_CHECK        60000   // Check battery every 60 seconds
#define LIVE_NETWORK_CHECK        10000   // Check GPRS/MQTT health every 10 seconds
#define LIVE_RECOVER_THRESHOLD    5       // Consecutive send fails -> force recoverModem()
#define LIVE_MIN_VOLTAGE          3.3     // Auto-stop below this voltage to protect battery
#define LIVE_SD_BATCH             10      // Buffer N points on SD before retrying network

// ---- BATCH mode settings ----
#define uS_TO_S_FACTOR  1000000ULL
#define TIME_TO_SLEEP   120         // Deep sleep interval (seconds)
#define BATCH_SIZE      1           // Points to collect before sending (1 = publish each wake)

// ---- SD card offline buffer (both modes) ----
// Each point ~80 bytes CSV. 100000 = ~8MB on a 32GB card
// At 1pt/sec live mode: 100000 pts = ~27 hours offline storage
#define SD_BUFFER_MAX   100000
#define SD_BUFFER_FILE  "/gps_buffer.csv"

// ---- H02 UDP direct-to-Traccar (optional, enable in config_local.h) ----
// #define H02_UDP_MODE
// Sends an H02 text datagram directly to Traccar via UDP on each GPS fix.
// Simple text format, no binary encoding. Router must forward H02_UDP_PORT to Traccar.
// Traccar auto-creates a device using H02_DEVICE_ID.
#ifndef H02_SERVER
  #define H02_SERVER      "YOUR_TRACCAR_HOST"
#endif
#ifndef H02_UDP_PORT
  #define H02_UDP_PORT    5023
#endif
#ifndef H02_DEVICE_ID
  #define H02_DEVICE_ID   "mototracker"  // must match Traccar device identifier
#endif

// ---- GT06 direct-to-Traccar (optional, enable in config_local.h) ----
// #define GT06_MODE
// When enabled, sends a GT06 binary TCP packet directly to Traccar on each GPS fix,
// in addition to (or instead of) MQTT. Router must forward GT06_PORT to Traccar host.
// Traccar will auto-create a device entry using the modem's IMEI.
#ifndef GT06_SERVER
  #define GT06_SERVER  "YOUR_TRACCAR_HOST"
#endif
#ifndef GT06_PORT
  #define GT06_PORT    5013
#endif

// ---- SD card log file ----
// Human-readable log of all events (network, GPS, MQTT, errors).
// Appended across reboots; each boot starts with a === BOOT === separator.
// At 1pt/sec ~200 bytes/line → ~17 MB/day → 29 GB lasts years.
#define SD_LOG_FILE     "/gpslog.txt"

// GPS fix timeout (milliseconds) — used in BATCH mode initial fix
#define GPS_TIMEOUT 120000

// ---- WiFi AP configuration portal ----
// Triggered by holding BOOT button (GPIO 0) for WIFI_CONFIG_HOLD_MS at startup.
// AP is shut down immediately after save / reset / timeout.
// All settings saved to CONFIG_JSON_FILE on SD card.
#define CONFIG_JSON_FILE         "/config.json"
#define WIFI_CONFIG_SSID         "MotoTracker-Setup"
#define WIFI_CONFIG_PASS         "mototrack"
#define WIFI_CONFIG_PIN          0          // GPIO 0 = BOOT button (active LOW)
#define WIFI_CONFIG_HOLD_MS      3000       // hold 3 s to trigger config mode
#define WIFI_CONFIG_TIMEOUT_MS   300000     // 5-minute AP auto-shutdown
