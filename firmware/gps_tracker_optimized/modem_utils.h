// modem_utils.h — Modem init, network connect, GPS control
#pragma once

// Uses globals: modem (TinyGsm), cfg (RuntimeConfig), sdLog()

// ---- Modem init ----
bool initModem() {
    sdLog("Initializing modem...");
    if (!modem.init()) {
        sdLog("Modem init failed, trying restart...");
        if (!modem.restart()) {
            sdLog("Modem restart failed");
            return false;
        }
    }

    sdLog("Modem: %s", modem.getModemName().c_str());

    // Turn off GPS power initially
    modem.sendAT("+CGPIO=0,48,1,0");
    modem.waitResponse(10000L);

    // Radio off for configuration
    modem.sendAT("+CFUN=0");
    modem.waitResponse(10000L);
    delay(200);

    // Network mode from runtime config (13=GSM only, 2=Auto, 38=CAT-M)
    modem.setNetworkMode(cfg.network_mode);
    delay(200);

    // When Auto, prefer CAT-M + NB-IoT (more stable TCP than 2G)
    if (cfg.network_mode == 2) {
        modem.setPreferredMode(3);
        delay(200);
    }

    // Radio on
    modem.sendAT("+CFUN=1");
    modem.waitResponse(10000L);
    delay(1000);

    return true;
}

// ---- Enable GPS (keep on) ----
bool enableGPS() {
    Serial.println("Enabling GPS...");
    modem.sendAT("+CGPIO=0,48,1,1");
    if (modem.waitResponse(10000L) != 1) {
        Serial.println("GPS CGPIO warn (continuing)");
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

// ---- Get GPS reading (non-blocking) ----
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

    if (speed < 0) speed = 0;  // TinyGsm returns -9999 when speed unavailable

    return true;
}

// ---- Wait for initial GPS fix (blocking, with timeout) ----
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

// ---- Network connect (SIM unlock, wait for registration, GPRS) ----
bool connectNetwork() {
#if TINY_GSM_TEST_GPRS
    if (GSM_PIN && modem.getSimStatus() != 3) {
        modem.simUnlock(GSM_PIN);
    }

    sdLog("SIM status: %d", modem.getSimStatus());
    sdLog("Signal quality: %d", modem.getSignalQuality());
    sdLog("Waiting for network...");
    if (!modem.waitForNetwork(180000L)) {
        sdLog("Network timeout");
        return false;
    }
    sdLog("Network registered");
    delay(3000);  // settle before GPRS

    // Pre-configure PDP context
    {
        char cgdcont[80];
        snprintf(cgdcont, sizeof(cgdcont),
                 "+CGDCONT=1,\"IP\",\"%s\",\"0.0.0.0\",0,0,0,0", cfg.apn);
        modem.sendAT(cgdcont);
    }
    modem.waitResponse(5000L);

    sdLog("Connecting GPRS: %s", cfg.apn);
    if (!modem.gprsConnect(cfg.apn, cfg.gprs_user, cfg.gprs_pass)) {
        sdLog("GPRS connect failed");
        return false;
    }

    if (modem.isGprsConnected()) {
        IPAddress ip = modem.localIP();
        sdLog("GPRS OK, IP: %s, signal: %d", ip.toString().c_str(), modem.getSignalQuality());
        return true;
    }
#endif
    return false;
}

// ---- Hard power cycle via PWR_PIN (SIM7070 power-key pulse) ----
void hardPowerCycleModem() {
    sdLog("Hard power-cycling modem (PWR_PIN pulse)");
    digitalWrite(PWR_PIN, HIGH);
    delay(100);
    digitalWrite(PWR_PIN, LOW);
    delay(1200);
    digitalWrite(PWR_PIN, HIGH);
    delay(5000);  // modem boot
}

// ---- Radio cycle: CFUN=0 -> CFUN=1 (fastest recovery, re-registers) ----
bool radioCycle() {
    sdLog("Cycling radio: CFUN=0 -> CFUN=1");
    modem.sendAT("+CFUN=0");
    if (modem.waitResponse(10000L) != 1) {
        sdLog("CFUN=0 no response");
        return false;
    }
    delay(1500);
    modem.sendAT("+CFUN=1");
    if (modem.waitResponse(10000L) != 1) {
        sdLog("CFUN=1 no response");
        return false;
    }
    delay(3000);
    return true;
}

// ---- Escalating modem recovery: radio cycle -> restart -> hard power cycle ----
// Returns true if GPRS is back up.  GPS must stay OFF throughout — the caller
// (loop) already suspended it before entering the send block.
bool recoverModem() {
    sdLog("Recovery L1: radio cycle");
    if (radioCycle() && connectNetwork()) {
        sdLog("Recovery L1 OK");
        return true;
    }
    delay(5000);  // let carrier-side PDP state settle before next level

    sdLog("Recovery L2: PDP teardown + full re-init");
    modem.sendAT("+CNACT=0,0");
    modem.waitResponse(5000L);
    modem.sendAT("+CGATT=0");
    modem.waitResponse(10000L);
    delay(3000);
    if (initModem() && connectNetwork()) {
        sdLog("Recovery L2 OK");
        return true;
    }
    delay(10000);

    sdLog("Recovery L3: hard power cycle + re-init");
    hardPowerCycleModem();
    if (!initModem()) {
        sdLog("Recovery L3: initModem failed");
        return false;
    }
    delay(5000);  // extra settle after full re-init
    if (!connectNetwork()) {
        sdLog("Recovery L3: connectNetwork failed");
        return false;
    }
    sdLog("Recovery L3 OK");
    return true;
}
