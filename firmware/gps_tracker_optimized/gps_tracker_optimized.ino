/*
   GPS Tracker - Optimized v4 (refactored)
   Hardware: LilyGO T-SIM7000G

   Two modes (select in config.h):

   LIVE MODE:
   - GPS and modem always on, sends every 1 second
   - MQTT_MODE: publishes JSON to broker -> HA automation -> Traccar + RPi
   - HTTP mode: direct GET to PHP backend (keep-alive)
   - SD card buffer when send fails
   - Auto-stop on low battery
   - LED blink = sending, LED solid = no GPS fix

   BATCH MODE:
   - Deep sleep between fixes
   - RTC memory batching (N points per send cycle)
   - SD card offline buffer
   - Low power consumption

   File structure:
     config.h          - hardware pins, timing, #define flags
     config_local.h    - credentials/overrides (gitignored)
     types.h           - GpsPoint struct
     sd_utils.h        - SD logging, battery, offline buffer
     modem_utils.h     - modem init, network, GPS control
     protocol_mqtt.h   - MQTT connect
     protocol_gt06.h   - GT06 binary TCP (optional)
     protocol_h02.h    - H02 text UDP (optional)
     wifi_config.h     - WiFi AP config portal + RuntimeConfig
*/

// ============================================================
// Includes — order matters: config first, then system libs,
// then wifi_config (defines RuntimeConfig cfg), then globals,
// then module headers (which reference the globals).
// ============================================================
#include "config.h"
#include <TinyGsmClient.h>
#include <SPI.h>
#include <SD.h>
#include "wifi_config.h"   // RuntimeConfig cfg, loadConfig, checkConfigButton...

#ifdef MQTT_MODE
  #include <PubSubClient.h>
#else
  #include <ArduinoHttpClient.h>
#endif

// ============================================================
// Global objects
// ============================================================
#ifdef DUMP_AT_COMMANDS
#include <StreamDebugger.h>
StreamDebugger debugger(SerialAT, Serial);
TinyGsm modem(debugger);
#else
TinyGsm modem(SerialAT);
#endif

TinyGsmClient client(modem);
bool sdAvailable = false;

#ifdef MQTT_MODE
PubSubClient mqtt(client);
#else
HttpClient http(client, IOT_SERVER, IOT_SERVER_PORT);
#endif

#ifdef GT06_MODE
TinyGsmClient gt06Client(modem, 1);
#endif

// ============================================================
// Module headers (all globals above are now visible)
// ============================================================
#include "types.h"
#include "sd_utils.h"
#include "modem_utils.h"
#include "protocol_gt06.h"
#include "protocol_h02.h"
#include "protocol_mqtt.h"

// ============================================================
// Mode-specific state
// ============================================================
#ifdef TRACKING_MODE_BATCH
RTC_DATA_ATTR GpsPoint rtcBuffer[BATCH_SIZE];
RTC_DATA_ATTR int rtcCount = 0;
RTC_DATA_ATTR int bootCount = 0;
#endif

#ifdef TRACKING_MODE_LIVE
bool gpsReady = false;
bool networkReady = false;
unsigned long lastSendTime = 0;
unsigned long lastBatteryCheck = 0;
unsigned long lastNetworkCheck = 0;
float lastBattery = 4.2;
int sendOkCount = 0;
int sendFailCount = 0;
int sdBufferCount = 0;
int consecutiveFailCount = 0;
#endif

// ============================================================
// sendPoint — orchestrates all enabled protocols
// ============================================================
bool sendPoint(GpsPoint &p, bool keepAlive) {
#ifdef MQTT_MODE
    // Reconnect per publish. The SIM7070's TCP stack on 2G/CAT-M gets wedged
    // after ~30 s of sustained use on a single socket, so we keep each socket
    // short-lived: open -> publish -> close. Matches the proven working
    // reference firmware (which achieves the same via sendGpsPacket's
    // client.stop() side effect).
    if (!mqtt.connected()) {
        if (!connectMQTT()) return false;
    }

    char payload[200];
    snprintf(payload, sizeof(payload),
        "{\"latitude\":%.6f,\"longitude\":%.6f,\"speed\":%.1f,"
        "\"temperature\":%.1f,\"humidity\":%.1f,\"battery\":%.2f,"
        "\"timestamp\":\"%s\"}",
        p.lat, p.lon, p.speed,
        p.temperature, p.humidity, p.battery,
        p.timestamp);

    bool ok = mqtt.publish(cfg.mqtt_topic, payload, true);
    mqtt.loop();
    sdLog("MQTT %s: %s", ok ? "OK" : "FAIL", payload);

    // Tear down the MQTT TCP socket so the next publish starts fresh.
    mqtt.disconnect();

#ifdef GT06_MODE
    sendGT06(p);      // best-effort alongside MQTT
#endif
#ifdef H02_UDP_MODE
    sendH02UDP(p);    // best-effort alongside MQTT
#endif

    return ok;

#else
    // HTTP GET to PHP backend (original)
    String request = "/gpstrack/gps_tracker_add_data_to_db.php?k=";
    request += API_KEY;
    request += "&v=";
    request += TABLE_NAME;
    request += "&la=";
    request += String(p.lat, GPS_PRECISION);
    request += "&lo=";
    request += String(p.lon, GPS_PRECISION);
    request += "&s=";
    request += String(p.speed, 1);
    request += "&t=";
    request += String(p.temperature, 1);
    request += "&h=";
    request += String(p.humidity, 1);
    request += "&b=";
    request += String(p.battery, 2);

    if (p.timestamp[0] != '\0') {
        request += "&ts=";
        String ts = String(p.timestamp);
        ts.replace(" ", "+");
        request += ts;
    }

    if (keepAlive) {
        http.connectionKeepAlive();
    }

    int err = http.get(request);
    if (err != 0) {
        Serial.println("HTTP failed");
        return false;
    }

    int status = http.responseStatusCode();
    String body = http.responseBody();

    if (!keepAlive) {
        http.stop();
    }

    return (status == 200 && body.indexOf("OK") >= 0);
#endif
}

// ============================================================
// SD buffer: send all buffered points
// ============================================================
int sendSDBuffer(bool keepAlive) {
    if (!sdAvailable) return 0;
    if (!SD.exists(SD_BUFFER_FILE)) return 0;

    File f = SD.open(SD_BUFFER_FILE, FILE_READ);
    if (!f) return 0;

    int sent = 0;
    int failed = 0;

    while (f.available()) {
        String line = f.readStringUntil('\n');
        line.trim();
        if (line.length() == 0) continue;

        GpsPoint p;
        int field = 0;
        int lastComma = -1;

        for (int i = 0; i <= (int)line.length(); i++) {
            if (i == (int)line.length() || line[i] == ',') {
                String val = line.substring(lastComma + 1, i);
                switch (field) {
                    case 0: p.lat = val.toFloat(); break;
                    case 1: p.lon = val.toFloat(); break;
                    case 2: p.speed = val.toFloat(); break;
                    case 3: p.temperature = val.toFloat(); break;
                    case 4: p.humidity = val.toFloat(); break;
                    case 5: p.battery = val.toFloat(); break;
                    case 6: val.toCharArray(p.timestamp, 20); break;
                }
                lastComma = i;
                field++;
            }
        }

        if (field >= 7) {
            if (p.speed < 0) p.speed = 0;  // clamp -9999 from old SD entries
            if (sendPoint(p, keepAlive)) {
                sent++;
            } else {
                failed++;
                break;
            }
            delay(100);
        }
    }
    f.close();

    if (failed == 0) {
        SD.remove(SD_BUFFER_FILE);
        sdLog("SD buffer cleared (%d points sent)", sent);
    } else {
        sdLog("SD buffer: %d sent, %d remaining", sent, failed);
    }

    return sent;
}

// ============================================================
// Forward declarations
// ============================================================
#ifdef TRACKING_MODE_LIVE
void setupLive();
#endif

// ============================================================
// setup() — shared hardware init
// ============================================================
void setup() {
    Serial.begin(115200);
    delay(10);

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, HIGH);

    // Power on modem
    pinMode(PWR_PIN, OUTPUT);
    digitalWrite(PWR_PIN, HIGH);
    delay(1000);
    digitalWrite(PWR_PIN, LOW);

    // Init SD card
    SPI.begin(SD_SCLK, SD_MISO, SD_MOSI, SD_CS);
    if (SD.begin(SD_CS)) {
        sdAvailable = true;
        Serial.printf("SD card: %lu MB\n", SD.cardSize() / (1024 * 1024));
        File f = SD.open(SD_LOG_FILE, FILE_APPEND);
        if (f) {
            f.printf("\n=== BOOT at millis=0, SD %lu MB ===\n",
                     SD.cardSize() / (1024 * 1024));
            f.close();
        }
    } else {
        Serial.println("SD card not available");
    }

    // Load runtime config from SD (/config.json overrides config.h defaults)
    loadConfig(cfg);

    // Enter WiFi config portal if:
    //   - /config.json is missing (first boot, or after "Reset to defaults"), OR
    //   - BOOT button (GPIO 0) is held for 3 s within the startup window.
    // To force config mode on a board without a BOOT button, simply delete
    // /config.json on the SD card and power-cycle.
    bool needConfig = !configExists();
    if (needConfig) {
        Serial.println("No /config.json on SD — entering WiFi config portal");
    }
    if (needConfig || checkConfigButton()) {
        startWiFiConfig(cfg);
    }

    delay(1000);
    SerialAT.begin(UART_BAUD, SERIAL_8N1, PIN_RX, PIN_TX);
    analogSetAttenuation(ADC_11db);

#ifdef TRACKING_MODE_LIVE
    Serial.println("\n=== MotoTracker LIVE MODE ===");
    Serial.printf("Interval: %d ms\n", LIVE_INTERVAL_MS);
    setupLive();
#endif

#ifdef TRACKING_MODE_BATCH
    bootCount++;
    Serial.printf("\n=== MotoTracker BATCH MODE - Boot #%d, buffer: %d/%d ===\n",
                  bootCount, rtcCount, BATCH_SIZE);
#endif
}

// ================================================================
// LIVE MODE
// ================================================================
#ifdef TRACKING_MODE_LIVE

void setupLive() {
    while (!initModem()) {
        Serial.println("Modem init retry in 5s...");
        delay(5000);
    }

    while (!enableGPS()) {
        Serial.println("GPS enable retry in 3s...");
        delay(3000);
    }

    // Wait for first GPS fix
    float lat, lon, speed;
    char ts[20];
#ifdef TEST_DUMMY_GPS
    Serial.println("TEST_DUMMY_GPS: skipping initial fix wait");
#else
    if (cfg.dummy_gps) {
        Serial.println("cfg.dummy_gps: skipping initial fix wait");
    } else {
        while (!waitForGpsFix(lat, lon, speed, ts)) {
            Serial.println("GPS fix retry...");
            delay(2000);
        }
    }
#endif

    // Suspend GPS engine during network attach — SIM7000 docs show GPS and TCP
    // as sequential, not concurrent. Running both causes CAOPEN error 23.
    modem.disableGPS();
    while (!connectNetwork()) {
        Serial.println("Network retry in 5s...");
        delay(5000);
    }
    networkReady = true;

    // Replay any SD-buffered points before re-enabling GPS — GPS must stay off
    // during TCP (SIM7000 sequential constraint). enableGPS() comes after.
    int recovered = sendSDBuffer(true);
    if (recovered > 0) {
        Serial.printf("Recovered %d offline points\n", recovered);
    }

    modem.enableGPS();  // restart GPS polling after SD buffer replay is done

#ifdef MQTT_MODE
    mqtt.setSocketTimeout(10);  // bound MQTT keepalive reads so loop can't stall
    while (!connectMQTT()) {
        Serial.println("MQTT retry in 5s...");
        delay(5000);
    }
#endif

    lastBattery = readBattery();
    sdLog("Battery: %.2fV", lastBattery);
    sdLog("=== LIVE TRACKING STARTED ===");
}

void loop() {
    unsigned long now = millis();

    // ---- Battery check (voltage only) ----
    if (now - lastBatteryCheck >= LIVE_BATTERY_CHECK) {
        lastBatteryCheck = now;
        lastBattery = readBattery();
        sdLog("[BATT] %.2fV | OK:%d FAIL:%d SD:%d",
              lastBattery, sendOkCount, sendFailCount, sdBufferCount);

        if (lastBattery > 1.0f && lastBattery < LIVE_MIN_VOLTAGE) {
            sdLog("LOW BATTERY — stopping tracker");
            disableGPS();
            modem.gprsDisconnect();
            modem.sendAT("+CPOWD=1");
            modem.waitResponse(10000L);
            esp_deep_sleep_start();
        }
    }

    // ---- Network health check (consecutive send failures only) ----
    // Do NOT poll isGprsConnected() here — a transient CGATT blip (often
    // caused by URCs from per-publish mqtt.disconnect() or CGNSPWR toggles)
    // would otherwise kick off a 2 min recovery cascade on a healthy link.
    // Let real send failures accumulate to the threshold instead.
    if (now - lastNetworkCheck >= LIVE_NETWORK_CHECK) {
        lastNetworkCheck = now;

        bool needRecover = consecutiveFailCount >= LIVE_RECOVER_THRESHOLD;

        if (needRecover) {
            sdLog("Network unhealthy (consecFail=%d), recovering...",
                  consecutiveFailCount);
#ifdef GT06_MODE
            gt06Reset();
#endif
            modem.disableGPS();
            networkReady = recoverModem();
            if (networkReady) {
                int recovered = sendSDBuffer(true);
                if (recovered > 0) {
                    sdBufferCount -= recovered;
                    if (sdBufferCount < 0) sdBufferCount = 0;
                }
            }
            modem.enableGPS();
            consecutiveFailCount = 0;
        } else {
            networkReady = true;
        }
    }

#ifdef MQTT_MODE
    mqtt.loop();
#endif

    // ---- Send at interval ----
    if (now - lastSendTime >= LIVE_INTERVAL_MS) {
        lastSendTime = now;

        float lat, lon, speed;
        char timestamp[20];

        bool hasFix = getGpsReading(lat, lon, speed, timestamp);
#ifdef TEST_DUMMY_GPS
        if (!hasFix) {
            lat = cfg.test_lat; lon = cfg.test_lon; speed = 0.0f;
            timestamp[0] = '\0';
            hasFix = true;
            Serial.println("[DUMMY GPS compile]");
        }
#else
        if (!hasFix && cfg.dummy_gps) {
            lat = cfg.test_lat; lon = cfg.test_lon; speed = 0.0f;
            timestamp[0] = '\0';
            hasFix = true;
            Serial.println("[DUMMY GPS runtime]");
        }
#endif
        if (hasFix) {
            digitalWrite(LED_PIN, LOW);

            GpsPoint p;
            p.lat = lat;
            p.lon = lon;
            p.speed = speed;
            p.temperature = 0;
            p.humidity = 0;
            p.battery = lastBattery;
            strncpy(p.timestamp, timestamp, 19);
            p.timestamp[19] = '\0';

            // Suspend GPS engine before opening any TCP socket.
            // SIM7000 docs show GPS and TCP as sequential; concurrent use
            // causes CAOPEN error 23. modem.disableGPS() / enableGPS() only
            // toggle AT+CGNSPWR — no hardware GPIO cycling, so fix is preserved.
            modem.disableGPS();

            if (networkReady && sendPoint(p, true)) {
                sendOkCount++;
                consecutiveFailCount = 0;
            } else {
                sendFailCount++;
                consecutiveFailCount++;
                if (savePointToSD(p)) {
                    sdBufferCount++;
                }

                if (sdBufferCount >= LIVE_SD_BATCH && networkReady) {
                    int recovered = sendSDBuffer(true);
                    if (recovered > 0) {
                        sdBufferCount -= recovered;
                        if (sdBufferCount < 0) sdBufferCount = 0;
                    }
                }
            }

            modem.enableGPS();  // restart GPS engine after TCP is done

            digitalWrite(LED_PIN, HIGH);
        } else {
            digitalWrite(LED_PIN, LOW);
        }
    }
}

#endif // TRACKING_MODE_LIVE

// ================================================================
// BATCH MODE
// ================================================================
#ifdef TRACKING_MODE_BATCH

void loop() {
    float lat = 0, lon = 0, speed = 0;
    char timestamp[20] = {0};

    if (!initModem()) {
        Serial.println("Modem init failed, sleeping...");
        goto batch_sleep;
    }

    if (!enableGPS()) {
        Serial.println("GPS enable failed, sleeping...");
        goto batch_sleep;
    }

    if (!waitForGpsFix(lat, lon, speed, timestamp)) {
        disableGPS();
        Serial.println("No GPS fix, sleeping...");
        goto batch_sleep;
    }

    disableGPS();

    {
        float battery = readBattery();
        Serial.printf("Battery: %.2f V\n", battery);

        rtcBuffer[rtcCount].lat = lat;
        rtcBuffer[rtcCount].lon = lon;
        rtcBuffer[rtcCount].speed = speed;
        rtcBuffer[rtcCount].temperature = 0;
        rtcBuffer[rtcCount].humidity = 0;
        rtcBuffer[rtcCount].battery = battery;
        strncpy(rtcBuffer[rtcCount].timestamp, timestamp, 19);
        rtcBuffer[rtcCount].timestamp[19] = '\0';
        rtcCount++;

        Serial.printf("RTC buffer: %d/%d points\n", rtcCount, BATCH_SIZE);

        if (rtcCount >= BATCH_SIZE) {
            if (connectNetwork()) {
                int sdSent = sendSDBuffer(false);
                if (sdSent > 0) {
                    Serial.printf("Recovered %d offline points\n", sdSent);
                }

                int ok = 0, fail = 0;
                for (int i = 0; i < rtcCount; i++) {
                    if (sendPoint(rtcBuffer[i], false)) {
                        ok++;
                    } else {
                        savePointToSD(rtcBuffer[i]);
                        fail++;
                    }
                    delay(300);
                }
                Serial.printf("Batch: %d sent, %d failed\n", ok, fail);
                rtcCount = 0;

                modem.gprsDisconnect();
                Serial.println("GPRS disconnected");
            } else {
                Serial.println("Network failed, saving to SD...");
                for (int i = 0; i < rtcCount; i++) {
                    savePointToSD(rtcBuffer[i]);
                }
                rtcCount = 0;
            }
        }
    }

batch_sleep:
    modem.sendAT("+CPOWD=1");
    modem.waitResponse(10000L);
    modem.poweroff();
    Serial.println("Modem off");

    Serial.printf("Sleep %d s\n", TIME_TO_SLEEP);
    esp_sleep_enable_timer_wakeup(TIME_TO_SLEEP * uS_TO_S_FACTOR);
    delay(200);
    esp_deep_sleep_start();
}

#endif // TRACKING_MODE_BATCH
