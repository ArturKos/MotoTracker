// sd_utils.h — SD card logging, battery reading, offline point buffer
#pragma once

#include <SD.h>
#include "types.h"

// Uses globals: sdAvailable (bool), SD_LOG_FILE, SD_BUFFER_FILE, ADC_PIN

// ---- SD logging ----
// Appends a timestamped line to SD_LOG_FILE.
// Always open/close so no data is lost on power cut. Also mirrors to Serial.
void sdLog(const char* fmt, ...) {
    char buf[220];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    Serial.println(buf);

    if (!sdAvailable) return;
    File f = SD.open(SD_LOG_FILE, FILE_APPEND);
    if (f) {
        f.printf("[%lu] %s\n", millis(), buf);
        f.close();
    }
}

// ---- Battery voltage via ADC ----
float readBattery() {
    uint32_t mv = analogReadMilliVolts(ADC_PIN);
    float voltage = (mv / 1000.0) * 2.0;
    return voltage;
}

// ---- Save a single GPS point to SD offline buffer ----
bool savePointToSD(GpsPoint &p) {
    if (!sdAvailable) return false;

    File f = SD.open(SD_BUFFER_FILE, FILE_APPEND);
    if (!f) {
        sdLog("SD buffer write failed");
        return false;
    }

    f.printf("%.6f,%.6f,%.1f,%.1f,%.1f,%.2f,%s\n",
             p.lat, p.lon, p.speed,
             p.temperature, p.humidity, p.battery,
             p.timestamp);
    f.close();
    return true;
}
