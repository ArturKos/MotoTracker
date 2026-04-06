/*
   GPS Tracker - Optimized HTTP version (v3)
   Hardware: LilyGO T-SIM7000G

   Two modes (select in config.h):

   LIVE MODE:
   - GPS and modem always on, sends every 1 second
   - HTTP keep-alive (reuses TCP connection)
   - SD card buffer when HTTP send fails
   - Auto-stop on low battery
   - LED blink = sending, LED solid = no GPS fix

   BATCH MODE:
   - Deep sleep between fixes
   - RTC memory batching (N points per send cycle)
   - SD card offline buffer
   - Low power consumption
*/

#include <ArduinoHttpClient.h>
#include "config.h"
#include <TinyGsmClient.h>
#include <SPI.h>
#include <SD.h>

#ifdef DUMP_AT_COMMANDS
#include <StreamDebugger.h>
StreamDebugger debugger(SerialAT, Serial);
TinyGsm modem(debugger);
#else
TinyGsm modem(SerialAT);
#endif

TinyGsmClient client(modem);
HttpClient http(client, IOT_SERVER, IOT_SERVER_PORT);

bool sdAvailable = false;

// ---- Data structure ----
struct GpsPoint {
    float lat;
    float lon;
    float speed;
    float temperature;
    float humidity;
    float battery;
    char  timestamp[20];  // "YYYY-MM-DD HH:MM:SS"
};

#ifdef TRACKING_MODE_BATCH
RTC_DATA_ATTR GpsPoint rtcBuffer[BATCH_SIZE];
RTC_DATA_ATTR int rtcCount = 0;
RTC_DATA_ATTR int bootCount = 0;
#endif

// ---- Live mode state ----
#ifdef TRACKING_MODE_LIVE
bool gpsReady = false;
bool networkReady = false;
unsigned long lastSendTime = 0;
unsigned long lastBatteryCheck = 0;
float lastBattery = 4.2;
int sendOkCount = 0;
int sendFailCount = 0;
int sdBufferCount = 0;
#endif

// ---- Battery ----
float readBattery() {
    uint32_t mv = analogReadMilliVolts(ADC_PIN);
    float voltage = (mv / 1000.0) * 2.0;
    return voltage;
}

// ---- Modem init ----
bool initModem() {
    Serial.println("Initializing modem...");
    if (!modem.init()) {
        Serial.println("Modem init failed, trying restart...");
        if (!modem.restart()) {
            Serial.println("Modem restart failed");
            return false;
        }
    }

    Serial.println("Modem: " + modem.getModemName());

    // Turn off GPS power initially
    modem.sendAT("+CGPIO=0,48,1,0");
    modem.waitResponse(10000L);

    // Radio off for configuration
    modem.sendAT("+CFUN=0");
    modem.waitResponse(10000L);
    delay(200);

    // Network mode: 2=Automatic
    modem.setNetworkMode(2);
    delay(200);

    // Preferred mode: 3=CAT-M and NB-IoT
    modem.setPreferredMode(3);
    delay(200);

    // Radio on
    modem.sendAT("+CFUN=1");
    modem.waitResponse(10000L);
    delay(200);

    return true;
}

// ---- Enable GPS (keep on) ----
bool enableGPS() {
    Serial.println("Enabling GPS...");
    modem.sendAT("+CGPIO=0,48,1,1");
    if (modem.waitResponse(10000L) != 1) {
        Serial.println("GPS power on failed");
        return false;
    }
    modem.enableGPS();
    return true;
}

// ---- Disable GPS ----
void disableGPS() {
    modem.disableGPS();
    modem.sendAT("+CGPIO=0,48,1,0");
    modem.waitResponse(10000L);
}

// ---- Get GPS reading (non-blocking for live, blocking for batch) ----
bool getGpsReading(float &lat, float &lon, float &speed, char* timestamp) {
    if (!modem.getGPS(&lat, &lon, &speed)) {
        return false;
    }

    int year, month, day, hour, minute, second;
    if (modem.getGPSTime(&year, &month, &day, &hour, &minute, &second)) {
        snprintf(timestamp, 20, "%04d-%02d-%02d %02d:%02d:%02d",
                 year, month, day, hour, minute, second);
    } else {
        timestamp[0] = '\0';
    }

    return true;
}

// ---- Wait for initial GPS fix (blocking) ----
bool waitForGpsFix(float &lat, float &lon, float &speed, char* timestamp) {
    Serial.println("Waiting for GPS fix...");
    unsigned long startTime = millis();
    while (millis() - startTime < GPS_TIMEOUT) {
        if (getGpsReading(lat, lon, speed, timestamp)) {
            Serial.printf("GPS fix: %.6f, %.6f, %.1f km/h @ %s\n",
                           lat, lon, speed, timestamp);
            return true;
        }
        Serial.print(".");
        delay(1000);
    }
    Serial.println("\nGPS fix timeout");
    return false;
}

// ---- Network connect ----
bool connectNetwork() {
#if TINY_GSM_TEST_GPRS
    if (GSM_PIN && modem.getSimStatus() != 3) {
        modem.simUnlock(GSM_PIN);
    }

    Serial.println("Waiting for network...");
    if (!modem.waitForNetwork(60000L)) {
        Serial.println("Network timeout");
        return false;
    }
    Serial.println("Network connected");

    Serial.println("Connecting GPRS: " + String(apn));
    if (!modem.gprsConnect(apn, gprsUser, gprsPass)) {
        Serial.println("GPRS connect failed");
        return false;
    }

    if (modem.isGprsConnected()) {
        IPAddress ip = modem.localIP();
        Serial.print("GPRS OK, IP: ");
        Serial.println(ip);
        Serial.println("Signal: " + String(modem.getSignalQuality()));
        return true;
    }
#endif
    return false;
}

// ---- Send single point via HTTP GET (with keep-alive for live mode) ----
bool sendPoint(GpsPoint &p, bool keepAlive) {
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
}

// ---- SD card buffer: save point ----
bool savePointToSD(GpsPoint &p) {
    if (!sdAvailable) return false;

    File f = SD.open(SD_BUFFER_FILE, FILE_APPEND);
    if (!f) {
        Serial.println("SD write failed");
        return false;
    }

    f.printf("%.6f,%.6f,%.1f,%.1f,%.1f,%.2f,%s\n",
             p.lat, p.lon, p.speed,
             p.temperature, p.humidity, p.battery,
             p.timestamp);
    f.close();
    return true;
}

// ---- SD card buffer: send all buffered points ----
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
        Serial.printf("SD buffer cleared (%d points sent)\n", sent);
    } else {
        Serial.printf("SD buffer: %d sent, %d remaining\n", sent, failed);
    }

    return sent;
}

// ---- Hardware setup (shared) ----
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
    } else {
        Serial.println("SD card not available");
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
    // Init modem once
    while (!initModem()) {
        Serial.println("Modem init retry in 5s...");
        delay(5000);
    }

    // Enable GPS and keep it on
    while (!enableGPS()) {
        Serial.println("GPS enable retry in 3s...");
        delay(3000);
    }

    // Wait for first GPS fix
    float lat, lon, speed;
    char ts[20];
    while (!waitForGpsFix(lat, lon, speed, ts)) {
        Serial.println("GPS fix retry...");
        delay(2000);
    }

    // Connect network
    while (!connectNetwork()) {
        Serial.println("Network retry in 5s...");
        delay(5000);
    }

    // Flush any SD-buffered points from previous sessions
    int recovered = sendSDBuffer(true);
    if (recovered > 0) {
        Serial.printf("Recovered %d offline points\n", recovered);
    }

    lastBattery = readBattery();
    Serial.printf("Battery: %.2f V\n", lastBattery);
    Serial.println("=== LIVE TRACKING STARTED ===\n");
}

void loop() {
    unsigned long now = millis();

    // ---- Check battery periodically ----
    if (now - lastBatteryCheck >= LIVE_BATTERY_CHECK) {
        lastBatteryCheck = now;
        lastBattery = readBattery();
        Serial.printf("[BATT] %.2f V | OK: %d, FAIL: %d, SD: %d\n",
                      lastBattery, sendOkCount, sendFailCount, sdBufferCount);

        if (lastBattery > 0 && lastBattery < LIVE_MIN_VOLTAGE) {
            Serial.println("LOW BATTERY — stopping tracker");
            disableGPS();
            modem.gprsDisconnect();
            modem.sendAT("+CPOWD=1");
            modem.waitResponse(10000L);
            esp_deep_sleep_start();  // Sleep forever until USB power
        }

        // Check GPRS still connected
        if (!modem.isGprsConnected()) {
            Serial.println("GPRS lost, reconnecting...");
            networkReady = connectNetwork();
        } else {
            networkReady = true;
        }
    }

    // ---- Send at interval ----
    if (now - lastSendTime >= LIVE_INTERVAL_MS) {
        lastSendTime = now;

        float lat, lon, speed;
        char timestamp[20];

        if (getGpsReading(lat, lon, speed, timestamp)) {
            // LED blink = got fix
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

            if (networkReady && sendPoint(p, true)) {
                sendOkCount++;
            } else {
                // Network down — buffer to SD
                sendFailCount++;
                if (savePointToSD(p)) {
                    sdBufferCount++;
                }

                // Try to recover SD buffer when we have enough
                if (sdBufferCount >= LIVE_SD_BATCH && networkReady) {
                    int recovered = sendSDBuffer(true);
                    if (recovered > 0) {
                        sdBufferCount -= recovered;
                        if (sdBufferCount < 0) sdBufferCount = 0;
                    }
                }
            }

            digitalWrite(LED_PIN, HIGH);
        } else {
            // No GPS fix — LED solid on as indicator
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

    // Init modem
    if (!initModem()) {
        Serial.println("Modem init failed, sleeping...");
        goto batch_sleep;
    }

    // Get GPS fix
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

    // Store point in RTC buffer
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
