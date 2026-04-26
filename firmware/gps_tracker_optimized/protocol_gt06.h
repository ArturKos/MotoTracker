// protocol_gt06.h — GT06 binary TCP protocol direct to Traccar
// Sends login (0x01) + location (0x12) packets via socket 1.
// Traccar identifies the device by modem IMEI.
#pragma once

#include "types.h"

// Uses globals: modem (TinyGsm), gt06Client (TinyGsmClient),
//               cfg (RuntimeConfig), sdLog()

#ifdef GT06_MODE

// ---- CRC16-IBM lookup table ----
static const uint16_t GT06_CRC_TABLE[256] = {
    0x0000,0x1189,0x2312,0x329B,0x4624,0x57AD,0x6536,0x74BF,
    0x8C48,0x9DC1,0xAF5A,0xBED3,0xCA6C,0xDBE5,0xE97E,0xF8F7,
    0x1081,0x0108,0x3393,0x221A,0x56A5,0x472C,0x75B7,0x643E,
    0x9CC9,0x8D40,0xBFDB,0xAE52,0xDAED,0xCB64,0xF9FF,0xE876,
    0x2102,0x308B,0x0210,0x1399,0x6726,0x76AF,0x4434,0x55BD,
    0xAD4A,0xBCC3,0x8E58,0x9FD1,0xEB6E,0xFAE7,0xC87C,0xD9F5,
    0x3183,0x200A,0x1291,0x0318,0x77A7,0x662E,0x54B5,0x453C,
    0xBDCB,0xAC42,0x9ED9,0x8F50,0xFBEF,0xEA66,0xD8FD,0xC974,
    0x4204,0x538D,0x6116,0x709F,0x0420,0x15A9,0x2732,0x36BB,
    0xCE4C,0xDFC5,0xED5E,0xFCD7,0x8868,0x99E1,0xAB7A,0xBAF3,
    0x5285,0x430C,0x7197,0x601E,0x14A1,0x0528,0x37B3,0x263A,
    0xDECD,0xCF44,0xFDDF,0xEC56,0x98E9,0x8960,0xBBFB,0xAA72,
    0x6306,0x728F,0x4014,0x519D,0x2522,0x34AB,0x0630,0x17B9,
    0xEF4E,0xFEC7,0xCC5C,0xDDD5,0xA96A,0xB8E3,0x8A78,0x9BF1,
    0x7387,0x620E,0x5095,0x411C,0x35A3,0x242A,0x16B1,0x0738,
    0xFFCF,0xEE46,0xDCDD,0xCD54,0xB9EB,0xA862,0x9AF9,0x8B70,
    0x8408,0x9581,0xA71A,0xB693,0xC22C,0xD3A5,0xE13E,0xF0B7,
    0x0840,0x19C9,0x2B52,0x3ADB,0x4E64,0x5FED,0x6D76,0x7CFF,
    0x9489,0x8500,0xB79B,0xA612,0xD2AD,0xC324,0xF1BF,0xE036,
    0x18C1,0x0948,0x3BD3,0x2A5A,0x5EE5,0x4F6C,0x7DF7,0x6C7E,
    0xA50A,0xB483,0x8618,0x9791,0xE32E,0xF2A7,0xC03C,0xD1B5,
    0x2942,0x38CB,0x0A50,0x1BD9,0x6F66,0x7EEF,0x4C74,0x5DFD,
    0xB58B,0xA402,0x9699,0x8710,0xF3AF,0xE226,0xD0BD,0xC134,
    0x39C3,0x284A,0x1AD1,0x0B58,0x7FE7,0x6E6E,0x5CF5,0x4D7C,
    0xC60C,0xD785,0xE51E,0xF497,0x8028,0x91A1,0xA33A,0xB2B3,
    0x4A44,0x5BCD,0x6956,0x78DF,0x0C60,0x1DE9,0x2F72,0x3EFB,
    0xD68D,0xC704,0xF59F,0xE416,0x90A9,0x8120,0xB3BB,0xA232,
    0x5AC5,0x4B4C,0x79D7,0x685E,0x1CE1,0x0D68,0x3FF3,0x2E7A,
    0xE70E,0xF687,0xC41C,0xD595,0xA12A,0xB0A3,0x8238,0x93B1,
    0x6B46,0x7ACF,0x4854,0x59DD,0x2D62,0x3CEB,0x0E70,0x1FF9,
    0xF78F,0xE606,0xD49D,0xC514,0xB1AB,0xA022,0x92B9,0x8330,
    0x7BC7,0x6A4E,0x58D5,0x495C,0x3DE3,0x2C6A,0x1EF1,0x0F78,
};

static uint16_t gt06Crc(const uint8_t* data, int len) {
    uint16_t crc = 0xFFFF;
    while (len--) crc = (crc >> 8) ^ GT06_CRC_TABLE[(crc ^ *data++) & 0xFF];
    return ~crc;
}

static bool gt06LoggedIn = false;
static uint16_t gt06Serial = 0;

// Build a raw GT06 frame. buf must be at least 64 bytes.
static int gt06BuildFrame(uint8_t* buf, uint8_t protocol,
                          const uint8_t* payload, int payloadLen) {
    int i = 0;
    buf[i++] = 0x78; buf[i++] = 0x78;          // start
    buf[i++] = (uint8_t)(1 + payloadLen + 2);   // length: protocol(1) + payload + serial(2)
    buf[i++] = protocol;
    memcpy(&buf[i], payload, payloadLen); i += payloadLen;
    buf[i++] = (gt06Serial >> 8) & 0xFF;
    buf[i++] = gt06Serial & 0xFF;
    uint16_t crc = gt06Crc(&buf[2], i - 2);
    buf[i++] = (crc >> 8) & 0xFF;
    buf[i++] = crc & 0xFF;
    buf[i++] = 0x0D; buf[i++] = 0x0A;
    gt06Serial++;
    return i;
}

// Send GT06 login packet using modem IMEI (BCD-encoded, 8 bytes)
static bool gt06Login() {
    String imei = modem.getIMEI();
    while (imei.length() < 15) imei = "0" + imei;
    uint8_t payload[8] = {0};
    for (int i = 0; i < 8; i++) {
        uint8_t hi = (i * 2     < 15) ? (imei[i * 2]     - '0') : 0;
        uint8_t lo = (i * 2 + 1 < 15) ? (imei[i * 2 + 1] - '0') : 0;
        payload[i] = (hi << 4) | lo;
    }
    uint8_t frame[32];
    int len = gt06BuildFrame(frame, 0x01, payload, 8);
    gt06Client.write(frame, len);
    sdLog("GT06 login sent (IMEI: %s)", imei.c_str());
    return true;
}

// ---- Public API ----

// Send a GT06 location packet (protocol 0x12)
bool sendGT06(GpsPoint& p) {
    if (!gt06Client.connected()) {
        sdLog("GT06 connecting to %s:%d...", cfg.gt06_server, cfg.gt06_port);
        if (!gt06Client.connect(cfg.gt06_server, cfg.gt06_port)) {
            sdLog("GT06 connect failed");
            return false;
        }
        gt06LoggedIn = false;
    }

    if (!gt06LoggedIn) {
        gt06Login();
        delay(500);
        gt06LoggedIn = true;
    }

    // Parse timestamp
    int year = 0, month = 0, day = 0, hour = 0, min = 0, sec = 0;
    if (p.timestamp[0]) {
        sscanf(p.timestamp, "%d-%d-%d %d:%d:%d",
               &year, &month, &day, &hour, &min, &sec);
    }

    // Build location payload (18 bytes)
    uint8_t payload[18];
    int pi = 0;

    payload[pi++] = (uint8_t)(year % 100);
    payload[pi++] = (uint8_t)month;
    payload[pi++] = (uint8_t)day;
    payload[pi++] = (uint8_t)hour;
    payload[pi++] = (uint8_t)min;
    payload[pi++] = (uint8_t)sec;

    // GPS info byte: high nibble = 12 GPS bytes, low nibble = satellites (8)
    payload[pi++] = 0xC8;

    // Latitude (4 bytes big-endian): degrees * 1800000
    uint32_t lat_raw = (uint32_t)(fabs(p.lat) * 1800000.0f);
    payload[pi++] = (lat_raw >> 24) & 0xFF;
    payload[pi++] = (lat_raw >> 16) & 0xFF;
    payload[pi++] = (lat_raw >>  8) & 0xFF;
    payload[pi++] =  lat_raw        & 0xFF;

    // Longitude (4 bytes big-endian): degrees * 1800000
    uint32_t lon_raw = (uint32_t)(fabs(p.lon) * 1800000.0f);
    payload[pi++] = (lon_raw >> 24) & 0xFF;
    payload[pi++] = (lon_raw >> 16) & 0xFF;
    payload[pi++] = (lon_raw >>  8) & 0xFF;
    payload[pi++] =  lon_raw        & 0xFF;

    // Speed (1 byte, km/h)
    payload[pi++] = (uint8_t)p.speed;

    // Course & status (2 bytes)
    uint16_t cs = 0;
    if (p.lat >= 0) cs |= (1 << 10);
    if (p.lon >= 0) cs |= (1 << 11);
    cs |= (1 << 12);  // real time
    cs |= (1 << 13);  // GPS valid
    payload[pi++] = (cs >> 8) & 0xFF;
    payload[pi++] =  cs       & 0xFF;

    uint8_t frame[64];
    int len = gt06BuildFrame(frame, 0x12, payload, pi);
    gt06Client.write(frame, len);
    sdLog("GT06 OK: %.6f,%.6f spd=%.1f", p.lat, p.lon, p.speed);
    return true;
}

// Reset GT06 connection state (call on GPRS loss)
void gt06Reset() {
    gt06Client.stop();
    gt06LoggedIn = false;
}

#endif // GT06_MODE
