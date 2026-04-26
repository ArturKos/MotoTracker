// types.h — Shared data types for MotoTracker firmware
#pragma once

struct GpsPoint {
    float lat;
    float lon;
    float speed;
    float temperature;
    float humidity;
    float battery;
    char  timestamp[20];  // "YYYY-MM-DD HH:MM:SS"
};
