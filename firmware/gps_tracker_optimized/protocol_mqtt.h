// protocol_mqtt.h — MQTT connect/reconnect
#pragma once

// Uses globals: mqtt (PubSubClient), client (TinyGsmClient),
//               cfg (RuntimeConfig), sdLog()

#ifdef MQTT_MODE

// Per-device unique client ID (MAC-derived) so two devices/reboots
// never evict each other on the broker.
static void buildMqttClientId(char* out, size_t n) {
    uint64_t mac = ESP.getEfuseMac();
    snprintf(out, n, "mototracker-%04x%08x",
             (uint16_t)(mac >> 32), (uint32_t)mac);
}

bool connectMQTT() {
    // Pre-test TCP socket with a bounded 10 s timeout. TinyGsm's default
    // connect() blocks 75 s which freezes the main loop; bail fast if the
    // socket stack is wedged so the network health check can escalate.
    sdLog("MQTT TCP pretest %s:%d (10s)...", cfg.mqtt_broker, cfg.mqtt_port);
    if (!client.connect(cfg.mqtt_broker, (uint16_t)cfg.mqtt_port, 10)) {
        sdLog("MQTT TCP pretest failed — socket unreachable");
        return false;
    }
    client.stop();
    delay(200);

    char clientId[24];
    buildMqttClientId(clientId, sizeof(clientId));

    sdLog("MQTT connecting to %s:%d as %s...",
          cfg.mqtt_broker, cfg.mqtt_port, clientId);
    mqtt.setServer(cfg.mqtt_broker, (uint16_t)cfg.mqtt_port);
    if (mqtt.connect(clientId, cfg.mqtt_user, cfg.mqtt_pass)) {
        sdLog("MQTT connected");
        return true;
    }
    sdLog("MQTT failed, rc=%d", mqtt.state());
    return false;
}

#endif // MQTT_MODE
