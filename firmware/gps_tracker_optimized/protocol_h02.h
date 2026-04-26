// protocol_h02.h — H02 text UDP protocol direct to Traccar
// Sends *HQ,...# datagrams via AT+CAOPEN/CASEND (socket 2).
// Text-based, no binary encoding or CRC needed.
#pragma once

#include "types.h"

// Uses globals: modem (TinyGsm), SerialAT, cfg (RuntimeConfig), sdLog()

#ifdef H02_UDP_MODE

// Convert decimal degrees to NMEA DDMM.MMMM format
static void decimalToDDMM(float deg, char* buf, int intDigits) {
    float absDeg = fabs(deg);
    int d = (int)absDeg;
    float m = (absDeg - d) * 60.0f;
    if (intDigits == 2)
        snprintf(buf, 12, "%02d%07.4f", d, m);
    else
        snprintf(buf, 12, "%03d%07.4f", d, m);
}

bool sendH02UDP(GpsPoint& p) {
    // Parse timestamp "YYYY-MM-DD HH:MM:SS"
    int year = 0, month = 0, day = 0, hour = 0, min = 0, sec = 0;
    if (p.timestamp[0]) {
        sscanf(p.timestamp, "%d-%d-%d %d:%d:%d",
               &year, &month, &day, &hour, &min, &sec);
    }

    char latBuf[12], lonBuf[12];
    decimalToDDMM(p.lat, latBuf, 2);
    decimalToDDMM(p.lon, lonBuf, 3);

    char msg[160];
    snprintf(msg, sizeof(msg),
        "*HQ,%s,V1,%02d%02d%02d,A,%s,%c,%s,%c,%.2f,0,%02d%02d%02d#",
        cfg.h02_device_id,
        hour, min, sec,
        latBuf, (p.lat >= 0) ? 'N' : 'S',
        lonBuf, (p.lon >= 0) ? 'E' : 'W',
        p.speed,
        day, month, (year % 100));

    int msgLen = strlen(msg);

    // Open UDP socket (socket 2)
    modem.sendAT("+CAOPEN=2,0,\"UDP\",\"", cfg.h02_server, "\",", cfg.h02_port);
    if (modem.waitResponse(10000L) != 1) {
        sdLog("H02 UDP open failed");
        return false;
    }

    // Send datagram
    modem.sendAT("+CASEND=2,", msgLen);
    if (modem.waitResponse(3000L, ">") != 1) {
        modem.sendAT("+CACLOSE=2");
        modem.waitResponse(3000L);
        sdLog("H02 UDP send prompt failed");
        return false;
    }
    SerialAT.print(msg);
    bool ok = (modem.waitResponse(5000L) == 1);

    // Close socket immediately (UDP is fire-and-forget)
    modem.sendAT("+CACLOSE=2");
    modem.waitResponse(3000L);

    sdLog("H02 UDP %s: %s", ok ? "OK" : "FAIL", msg);
    return ok;
}

#endif // H02_UDP_MODE
