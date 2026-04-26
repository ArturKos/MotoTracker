// ============================================================
// wifi_config.h — WiFi AP Configuration Portal
// MotoTracker / LilyGO T-SIM7000G
//
// Trigger:   Hold BOOT button (GPIO 0) for 3 s at startup
// AP SSID:   WIFI_CONFIG_SSID  (defined in config.h, default "MotoTracker-Setup")
// AP is shut down immediately after save / reset / timeout.
// Config stored as /config.json on SD card.
// ============================================================
#pragma once

#include <WiFi.h>
#include <WebServer.h>
#include <DNSServer.h>
#include <ArduinoJson.h>

// Forward declarations (defined in the main .ino)
extern bool sdAvailable;

// ============================================================
// Runtime config struct — all values changeable via web UI
// Loaded from /config.json at boot; falls back to config.h defaults
// ============================================================
struct RuntimeConfig {
    // Cellular
    char  apn[32];
    char  gprs_user[32];
    char  gprs_pass[32];
    int   network_mode;        // 13=GSM only, 2=Auto, 38=CAT-M only

    // MQTT
    char  mqtt_broker[64];
    int   mqtt_port;
    char  mqtt_user[32];
    char  mqtt_pass[80];
    char  mqtt_topic[64];

    // Tracking
    int   live_interval_ms;
    float live_min_voltage;
    int   batch_size;
    int   time_to_sleep_s;

    // Optional pipelines
    char  gt06_server[64];
    int   gt06_port;
    char  h02_server[64];
    int   h02_port;
    char  h02_device_id[32];

    // Test / debug
    bool  dummy_gps;
    float test_lat;
    float test_lon;
};

RuntimeConfig cfg;

// ============================================================
// Apply compile-time defaults to a RuntimeConfig
// ============================================================
static void _cfgDefaults(RuntimeConfig& c) {
    strlcpy(c.apn,       APN,       sizeof(c.apn));
    strlcpy(c.gprs_user, GPRS_USER, sizeof(c.gprs_user));
    strlcpy(c.gprs_pass, GPRS_PASS, sizeof(c.gprs_pass));
    c.network_mode = 2;   // Auto (CAT-M / NB-IoT / GSM) — matches known-working firmware

#ifdef MQTT_BROKER
    strlcpy(c.mqtt_broker, MQTT_BROKER, sizeof(c.mqtt_broker));
#else
    c.mqtt_broker[0] = '\0';
#endif
#ifdef MQTT_PORT
    c.mqtt_port = MQTT_PORT;
#else
    c.mqtt_port = 1883;
#endif
#ifdef MQTT_USER
    strlcpy(c.mqtt_user, MQTT_USER, sizeof(c.mqtt_user));
#else
    c.mqtt_user[0] = '\0';
#endif
#ifdef MQTT_PASS
    strlcpy(c.mqtt_pass, MQTT_PASS, sizeof(c.mqtt_pass));
#else
    c.mqtt_pass[0] = '\0';
#endif
#ifdef MQTT_TOPIC
    strlcpy(c.mqtt_topic, MQTT_TOPIC, sizeof(c.mqtt_topic));
#else
    c.mqtt_topic[0] = '\0';
#endif

    c.live_interval_ms = LIVE_INTERVAL_MS;
    c.live_min_voltage = LIVE_MIN_VOLTAGE;
    c.batch_size       = BATCH_SIZE;
    c.time_to_sleep_s  = TIME_TO_SLEEP;

    strlcpy(c.gt06_server,   GT06_SERVER,   sizeof(c.gt06_server));
    c.gt06_port = GT06_PORT;
    strlcpy(c.h02_server,    H02_SERVER,    sizeof(c.h02_server));
    c.h02_port  = H02_UDP_PORT;
    strlcpy(c.h02_device_id, H02_DEVICE_ID, sizeof(c.h02_device_id));

#ifdef TEST_DUMMY_GPS
    c.dummy_gps = true;
    c.test_lat  = TEST_LAT;
    c.test_lon  = TEST_LON;
#else
    c.dummy_gps = false;
    c.test_lat  = 53.4285f;
    c.test_lon  = 14.5528f;
#endif
}

// ============================================================
// Load /config.json from SD card, override defaults.
// Returns true if file was found and parsed successfully.
// ============================================================
bool loadConfig(RuntimeConfig& c) {
    _cfgDefaults(c);

    if (!sdAvailable || !SD.exists(CONFIG_JSON_FILE)) {
        Serial.println("config.json not found — using compile-time defaults");
        return false;
    }

    File f = SD.open(CONFIG_JSON_FILE, FILE_READ);
    if (!f) { Serial.println("config.json open failed"); return false; }

#if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
#else
    DynamicJsonDocument doc(1024);
#endif

    DeserializationError err = deserializeJson(doc, f);
    f.close();
    if (err) { Serial.printf("config.json parse error: %s\n", err.c_str()); return false; }

    if (!doc["apn"].isNull())          strlcpy(c.apn,          doc["apn"],          sizeof(c.apn));
    if (!doc["gprs_user"].isNull())    strlcpy(c.gprs_user,    doc["gprs_user"],    sizeof(c.gprs_user));
    if (!doc["gprs_pass"].isNull())    strlcpy(c.gprs_pass,    doc["gprs_pass"],    sizeof(c.gprs_pass));
    if (!doc["network_mode"].isNull()) c.network_mode        = doc["network_mode"];

    if (!doc["mqtt_broker"].isNull())  strlcpy(c.mqtt_broker,  doc["mqtt_broker"],  sizeof(c.mqtt_broker));
    if (!doc["mqtt_port"].isNull())    c.mqtt_port           = doc["mqtt_port"];
    if (!doc["mqtt_user"].isNull())    strlcpy(c.mqtt_user,    doc["mqtt_user"],    sizeof(c.mqtt_user));
    if (!doc["mqtt_pass"].isNull())    strlcpy(c.mqtt_pass,    doc["mqtt_pass"],    sizeof(c.mqtt_pass));
    if (!doc["mqtt_topic"].isNull())   strlcpy(c.mqtt_topic,   doc["mqtt_topic"],   sizeof(c.mqtt_topic));

    if (!doc["live_interval_ms"].isNull())  c.live_interval_ms  = doc["live_interval_ms"];
    if (!doc["live_min_voltage"].isNull())  c.live_min_voltage  = doc["live_min_voltage"].as<float>();
    if (!doc["batch_size"].isNull())        c.batch_size        = doc["batch_size"];
    if (!doc["time_to_sleep_s"].isNull())   c.time_to_sleep_s   = doc["time_to_sleep_s"];

    if (!doc["gt06_server"].isNull())   strlcpy(c.gt06_server,   doc["gt06_server"],   sizeof(c.gt06_server));
    if (!doc["gt06_port"].isNull())     c.gt06_port           = doc["gt06_port"];
    if (!doc["h02_server"].isNull())    strlcpy(c.h02_server,    doc["h02_server"],    sizeof(c.h02_server));
    if (!doc["h02_port"].isNull())      c.h02_port            = doc["h02_port"];
    if (!doc["h02_device_id"].isNull()) strlcpy(c.h02_device_id, doc["h02_device_id"], sizeof(c.h02_device_id));

    if (!doc["dummy_gps"].isNull())  c.dummy_gps = doc["dummy_gps"].as<bool>();
    if (!doc["test_lat"].isNull())   c.test_lat  = doc["test_lat"].as<float>();
    if (!doc["test_lon"].isNull())   c.test_lon  = doc["test_lon"].as<float>();

    Serial.println("config.json loaded OK");
    return true;
}

// ============================================================
// Save RuntimeConfig to /config.json on SD card
// ============================================================
bool saveConfig(RuntimeConfig& c) {
    if (!sdAvailable) { Serial.println("saveConfig: SD not available"); return false; }

#if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
#else
    DynamicJsonDocument doc(1024);
#endif

    doc["apn"]             = c.apn;
    doc["gprs_user"]       = c.gprs_user;
    doc["gprs_pass"]       = c.gprs_pass;
    doc["network_mode"]    = c.network_mode;
    doc["mqtt_broker"]     = c.mqtt_broker;
    doc["mqtt_port"]       = c.mqtt_port;
    doc["mqtt_user"]       = c.mqtt_user;
    doc["mqtt_pass"]       = c.mqtt_pass;
    doc["mqtt_topic"]      = c.mqtt_topic;
    doc["live_interval_ms"]  = c.live_interval_ms;
    doc["live_min_voltage"]  = c.live_min_voltage;
    doc["batch_size"]        = c.batch_size;
    doc["time_to_sleep_s"]   = c.time_to_sleep_s;
    doc["gt06_server"]     = c.gt06_server;
    doc["gt06_port"]       = c.gt06_port;
    doc["h02_server"]      = c.h02_server;
    doc["h02_port"]        = c.h02_port;
    doc["h02_device_id"]   = c.h02_device_id;
    doc["dummy_gps"]       = c.dummy_gps;
    doc["test_lat"]        = c.test_lat;
    doc["test_lon"]        = c.test_lon;

    File f = SD.open(CONFIG_JSON_FILE, FILE_WRITE);
    if (!f) { Serial.println("saveConfig: cannot open file"); return false; }
    serializeJsonPretty(doc, f);
    f.close();
    Serial.println("config.json saved");
    return true;
}

// ============================================================
// Delete /config.json — next boot uses compile-time defaults
// ============================================================
void deleteConfig() {
    if (sdAvailable && SD.exists(CONFIG_JSON_FILE)) {
        SD.remove(CONFIG_JSON_FILE);
        Serial.println("config.json deleted — reset to defaults");
    }
}

// ============================================================
// True if /config.json exists on the SD card.
// When absent (first boot or after "Reset to defaults"), the
// firmware auto-enters the WiFi config portal — no button needed.
// ============================================================
bool configExists() {
    return sdAvailable && SD.exists(CONFIG_JSON_FILE);
}

// ============================================================
// Check if BOOT button (GPIO 0, active LOW) is held for
// WIFI_CONFIG_HOLD_MS within a short window at startup.
// Call once early in setup(), after Serial.begin().
// ============================================================
bool checkConfigButton() {
    pinMode(WIFI_CONFIG_PIN, INPUT_PULLUP);
    unsigned long windowEnd = millis() + (unsigned long)WIFI_CONFIG_HOLD_MS + 2000UL;
    Serial.printf("[ Hold BOOT for %ds to enter WiFi config mode ]\n",
                  WIFI_CONFIG_HOLD_MS / 1000);

    while (millis() < windowEnd) {
        if (digitalRead(WIFI_CONFIG_PIN) == LOW) {
            unsigned long pressStart = millis();
            Serial.print("BOOT held");
            while (digitalRead(WIFI_CONFIG_PIN) == LOW) {
                if (millis() - pressStart >= (unsigned long)WIFI_CONFIG_HOLD_MS) {
                    Serial.println(" -> WiFi config mode!");
                    return true;
                }
                Serial.print(".");
                delay(200);
            }
            Serial.println(" released too soon");
        }
        delay(50);
    }
    Serial.println("[ Normal boot ]");
    return false;
}

// ============================================================
// URL-decode a POST form value
// ============================================================
static String _urlDecode(const String& s) {
    String out;
    out.reserve(s.length());
    for (int i = 0; i < (int)s.length(); i++) {
        if (s[i] == '+') {
            out += ' ';
        } else if (s[i] == '%' && i + 2 < (int)s.length()) {
            auto hex = [](char c) -> int {
                if (c >= '0' && c <= '9') return c - '0';
                if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                if (c >= 'A' && c <= 'F') return c - 'A' + 10;
                return 0;
            };
            out += (char)((hex(s[i+1]) << 4) | hex(s[i+2]));
            i += 2;
        } else {
            out += s[i];
        }
    }
    return out;
}

// ============================================================
// Config page HTML — stored in flash (PROGMEM)
// Placeholders: %%KEY%% are replaced at runtime with live values
// ============================================================
static const char _CONFIG_HTML[] PROGMEM = R"html(<!DOCTYPE html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MotoTracker Config</title>
<style>
*{box-sizing:border-box}
body{font-family:sans-serif;max-width:500px;margin:0 auto;padding:16px;background:#111;color:#eee}
h2{color:#f90;margin:0 0 2px}
.sub{color:#888;font-size:.85em;margin-bottom:18px}
fieldset{border:1px solid #444;border-radius:6px;padding:10px 14px;margin-bottom:14px}
legend{color:#f90;font-weight:bold;padding:0 6px;font-size:.95em}
label{display:block;margin:8px 0 2px;font-size:.85em;color:#999}
input[type=text],input[type=number],input[type=password]{width:100%;padding:7px 9px;background:#1e1e1e;border:1px solid #555;border-radius:4px;color:#fff;font-size:.95em}
input:focus{outline:none;border-color:#f90}
.row{display:flex;gap:8px}
.row>div{flex:1}
.cb{display:flex;align-items:center;gap:10px;margin:10px 0 4px}
.cb input[type=checkbox]{width:18px;height:18px;accent-color:#f90}
.btn{display:block;width:100%;padding:13px;border:none;border-radius:6px;font-size:1em;cursor:pointer;margin-top:10px;font-weight:bold}
.save{background:#f90;color:#111}
.save:hover{background:#ffa820}
.reset{background:#900;color:#fff;margin-top:8px}
.reset:hover{background:#c00}
.note{font-size:.78em;color:#666;margin-top:14px;padding:8px;border:1px solid #333;border-radius:4px}
.timeout{color:#f90}
</style>
</head><body>
<h2>MotoTracker</h2>
<p class="sub">WiFi Config &mdash; connect to <b>%%SSID%%</b> and open this page</p>
<form method="POST" action="/save">

<fieldset><legend>Cellular</legend>
<label>APN</label>
<input type="text" name="apn" value="%%APN%%">
<div class="row">
  <div><label>GPRS user</label><input type="text" name="gprs_user" value="%%GPRS_USER%%"></div>
  <div><label>GPRS pass</label><input type="password" name="gprs_pass" value="%%GPRS_PASS%%"></div>
</div>
<label>Network mode &nbsp;<span style="color:#666;font-size:.9em">13=GSM&nbsp; 2=Auto&nbsp; 38=CAT-M</span></label>
<input type="number" name="network_mode" value="%%NETWORK_MODE%%">
</fieldset>

<fieldset><legend>MQTT</legend>
<label>Broker hostname / IP</label>
<input type="text" name="mqtt_broker" value="%%MQTT_BROKER%%">
<div class="row">
  <div><label>Port</label><input type="number" name="mqtt_port" value="%%MQTT_PORT%%"></div>
  <div><label>User</label><input type="text" name="mqtt_user" value="%%MQTT_USER%%"></div>
</div>
<label>Password</label>
<input type="password" name="mqtt_pass" value="%%MQTT_PASS%%">
<label>Topic</label>
<input type="text" name="mqtt_topic" value="%%MQTT_TOPIC%%">
</fieldset>

<fieldset><legend>Tracking</legend>
<label>Send interval (ms) &nbsp;<span style="color:#666;font-size:.9em">1000 = 1 pt/s</span></label>
<input type="number" name="live_interval_ms" value="%%LIVE_INTERVAL_MS%%">
<label>Min battery voltage (V) &nbsp;<span style="color:#666;font-size:.9em">stop below this</span></label>
<input type="number" name="live_min_voltage" value="%%LIVE_MIN_VOLTAGE%%" step="0.1">
<div class="row">
  <div><label>Batch size</label><input type="number" name="batch_size" value="%%BATCH_SIZE%%"></div>
  <div><label>Deep sleep (s)</label><input type="number" name="time_to_sleep_s" value="%%TIME_TO_SLEEP_S%%"></div>
</div>
</fieldset>

<fieldset><legend>Optional pipelines</legend>
<label>GT06 server</label>
<input type="text" name="gt06_server" value="%%GT06_SERVER%%">
<div class="row">
  <div><label>GT06 port</label><input type="number" name="gt06_port" value="%%GT06_PORT%%"></div>
</div>
<label>H02 server</label>
<input type="text" name="h02_server" value="%%H02_SERVER%%">
<div class="row">
  <div><label>H02 port</label><input type="number" name="h02_port" value="%%H02_PORT%%"></div>
  <div><label>H02 device ID</label><input type="text" name="h02_device_id" value="%%H02_DEVICE_ID%%"></div>
</div>
</fieldset>

<fieldset><legend>Test / Debug</legend>
<div class="cb">
  <input type="checkbox" name="dummy_gps" value="1" %%DUMMY_CHECKED%%>
  <label style="margin:0">Dummy GPS (fixed coordinates, no real fix needed)</label>
</div>
<div class="row">
  <div><label>Test lat</label><input type="number" name="test_lat" value="%%TEST_LAT%%" step="0.0001"></div>
  <div><label>Test lon</label><input type="number" name="test_lon" value="%%TEST_LON%%" step="0.0001"></div>
</div>
</fieldset>

<button type="submit" class="btn save">&#10003; Save &amp; Reboot</button>
</form>

<form method="POST" action="/reset" onsubmit="return confirm('Delete config.json and reboot to compile-time defaults?')">
<button type="submit" class="btn reset">&#9249; Reset to Defaults &amp; Reboot</button>
</form>

<div class="note">
  AT debug and LIVE/BATCH tracking mode require reflash to change.<br>
  Portal auto-closes in <span class="timeout">%%TIMEOUT%%s</span> if unused.
</div>
</body></html>
)html";

// ============================================================
// Start the WiFi AP config portal.
// Blocks until: user saves (→ reboot), user resets (→ reboot),
// or timeout expires (→ returns, continues normal boot).
// ============================================================
void startWiFiConfig(RuntimeConfig& c) {
    Serial.printf("\n=== WiFi Config Mode ===\nSSID: %s  Pass: %s\n",
                  WIFI_CONFIG_SSID, WIFI_CONFIG_PASS);

    WiFi.mode(WIFI_AP);
    WiFi.softAP(WIFI_CONFIG_SSID, WIFI_CONFIG_PASS);
    IPAddress apIP = WiFi.softAPIP();
    Serial.printf("AP IP: %s\n", apIP.toString().c_str());
    Serial.println("Connect phone/laptop to the AP and open the IP in a browser.");

    DNSServer dns;
    dns.start(53, "*", apIP);   // Captive portal: all DNS → our IP

    WebServer server(80);
    bool done = false;
    unsigned long configStart = millis();

    // ---- GET / — serve config form ----
    server.on("/", HTTP_GET, [&]() {
        String html = FPSTR(_CONFIG_HTML);
        unsigned long remaining = ((unsigned long)WIFI_CONFIG_TIMEOUT_MS
                                   - (millis() - configStart)) / 1000;
        html.replace("%%SSID%%",            WIFI_CONFIG_SSID);
        html.replace("%%APN%%",             c.apn);
        html.replace("%%GPRS_USER%%",       c.gprs_user);
        html.replace("%%GPRS_PASS%%",       c.gprs_pass);
        html.replace("%%NETWORK_MODE%%",    String(c.network_mode));
        html.replace("%%MQTT_BROKER%%",     c.mqtt_broker);
        html.replace("%%MQTT_PORT%%",       String(c.mqtt_port));
        html.replace("%%MQTT_USER%%",       c.mqtt_user);
        html.replace("%%MQTT_PASS%%",       c.mqtt_pass);
        html.replace("%%MQTT_TOPIC%%",      c.mqtt_topic);
        html.replace("%%LIVE_INTERVAL_MS%%",String(c.live_interval_ms));
        html.replace("%%LIVE_MIN_VOLTAGE%%",String(c.live_min_voltage, 1));
        html.replace("%%BATCH_SIZE%%",      String(c.batch_size));
        html.replace("%%TIME_TO_SLEEP_S%%", String(c.time_to_sleep_s));
        html.replace("%%GT06_SERVER%%",     c.gt06_server);
        html.replace("%%GT06_PORT%%",       String(c.gt06_port));
        html.replace("%%H02_SERVER%%",      c.h02_server);
        html.replace("%%H02_PORT%%",        String(c.h02_port));
        html.replace("%%H02_DEVICE_ID%%",   c.h02_device_id);
        html.replace("%%DUMMY_CHECKED%%",   c.dummy_gps ? "checked" : "");
        html.replace("%%TEST_LAT%%",        String(c.test_lat, 4));
        html.replace("%%TEST_LON%%",        String(c.test_lon, 4));
        html.replace("%%TIMEOUT%%",         String(remaining));
        server.send(200, "text/html", html);
    });

    // Captive portal redirects (Android, iOS, Windows detection URLs)
    auto redirect = [&]() { server.sendHeader("Location", "/"); server.send(302); };
    server.on("/generate_204",          HTTP_GET, redirect);
    server.on("/fwlink",                HTTP_GET, redirect);
    server.on("/connecttest.txt",       HTTP_GET, redirect);
    server.on("/hotspot-detect.html",   HTTP_GET, redirect);
    server.onNotFound([&]() { server.sendHeader("Location", "/"); server.send(302); });

    // ---- POST /save — apply and persist form data ----
    server.on("/save", HTTP_POST, [&]() {
        auto arg = [&](const char* key) -> String {
            if (server.hasArg(key)) return _urlDecode(server.arg(key));
            return String("");
        };

        if (server.hasArg("apn"))             strlcpy(c.apn,          arg("apn").c_str(),          sizeof(c.apn));
        if (server.hasArg("gprs_user"))       strlcpy(c.gprs_user,    arg("gprs_user").c_str(),    sizeof(c.gprs_user));
        if (server.hasArg("gprs_pass"))       strlcpy(c.gprs_pass,    arg("gprs_pass").c_str(),    sizeof(c.gprs_pass));
        if (server.hasArg("network_mode"))    c.network_mode       = arg("network_mode").toInt();
        if (server.hasArg("mqtt_broker"))     strlcpy(c.mqtt_broker,  arg("mqtt_broker").c_str(),  sizeof(c.mqtt_broker));
        if (server.hasArg("mqtt_port"))       c.mqtt_port          = arg("mqtt_port").toInt();
        if (server.hasArg("mqtt_user"))       strlcpy(c.mqtt_user,    arg("mqtt_user").c_str(),    sizeof(c.mqtt_user));
        if (server.hasArg("mqtt_pass"))       strlcpy(c.mqtt_pass,    arg("mqtt_pass").c_str(),    sizeof(c.mqtt_pass));
        if (server.hasArg("mqtt_topic"))      strlcpy(c.mqtt_topic,   arg("mqtt_topic").c_str(),   sizeof(c.mqtt_topic));
        if (server.hasArg("live_interval_ms"))c.live_interval_ms   = arg("live_interval_ms").toInt();
        if (server.hasArg("live_min_voltage"))c.live_min_voltage   = arg("live_min_voltage").toFloat();
        if (server.hasArg("batch_size"))      c.batch_size         = arg("batch_size").toInt();
        if (server.hasArg("time_to_sleep_s")) c.time_to_sleep_s    = arg("time_to_sleep_s").toInt();
        if (server.hasArg("gt06_server"))     strlcpy(c.gt06_server,  arg("gt06_server").c_str(),  sizeof(c.gt06_server));
        if (server.hasArg("gt06_port"))       c.gt06_port          = arg("gt06_port").toInt();
        if (server.hasArg("h02_server"))      strlcpy(c.h02_server,   arg("h02_server").c_str(),   sizeof(c.h02_server));
        if (server.hasArg("h02_port"))        c.h02_port           = arg("h02_port").toInt();
        if (server.hasArg("h02_device_id"))   strlcpy(c.h02_device_id, arg("h02_device_id").c_str(), sizeof(c.h02_device_id));
        // Checkbox: present = checked, absent = unchecked
        c.dummy_gps = server.hasArg("dummy_gps");
        if (server.hasArg("test_lat"))        c.test_lat           = arg("test_lat").toFloat();
        if (server.hasArg("test_lon"))        c.test_lon           = arg("test_lon").toFloat();

        bool ok = saveConfig(c);
        server.send(200, "text/html",
            String(F("<!DOCTYPE html><html><body style='background:#111;color:#eee;"
                     "font-family:sans-serif;text-align:center;padding:48px'>"))
            + "<h2 style='color:" + (ok ? "#0f0" : "#f00") + "'>"
            + (ok ? "Saved!" : "Save failed — SD error") + "</h2>"
            + (ok ? "<p>Rebooting in 2 s&hellip;</p>" : "<p>Check SD card and try again.</p>")
            + "</body></html>");

        if (ok) done = true;
    });

    // ---- POST /reset — delete config.json and reboot ----
    server.on("/reset", HTTP_POST, [&]() {
        deleteConfig();
        server.send(200, "text/html",
            F("<!DOCTYPE html><html><body style='background:#111;color:#eee;"
              "font-family:sans-serif;text-align:center;padding:48px'>"
              "<h2 style='color:#f90'>Reset!</h2>"
              "<p>config.json deleted. Rebooting to compile-time defaults&hellip;</p>"
              "</body></html>"));
        delay(1500);
        ESP.restart();
    });

    server.begin();
    Serial.printf("Portal ready — connect to WiFi '%s' and open http://%s\n",
                  WIFI_CONFIG_SSID, apIP.toString().c_str());

    // ---- Event loop ----
    bool ledState = false;
    unsigned long lastBlink = 0;

    while (!done) {
        dns.processNextRequest();
        server.handleClient();

        // Fast LED blink = config mode active
        if (millis() - lastBlink >= 150) {
            lastBlink = millis();
            ledState = !ledState;
            digitalWrite(LED_PIN, ledState ? LOW : HIGH);
        }

        // Auto-timeout
        if (millis() - configStart >= (unsigned long)WIFI_CONFIG_TIMEOUT_MS) {
            Serial.println("WiFi config timeout — continuing normal boot");
            break;
        }
        delay(2);
    }

    // Tear down AP unconditionally
    server.stop();
    dns.stop();
    WiFi.softAPdisconnect(true);
    WiFi.mode(WIFI_OFF);
    digitalWrite(LED_PIN, HIGH);  // restore LED off state
    Serial.println("WiFi AP stopped");

    if (done) {
        delay(2000);
        ESP.restart();
    }
    // Timeout path: fall through — normal boot continues with currently loaded cfg
}
