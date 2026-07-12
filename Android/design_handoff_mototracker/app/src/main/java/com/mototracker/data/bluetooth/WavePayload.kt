package com.mototracker.data.bluetooth

/**
 * Compact rider identity record broadcast over BLE advertisement service data.
 *
 * Encoded and decoded by [WavePayloadCodec]. All three fields are truncated
 * if necessary to keep the total wire size within [WavePayloadCodec.MAX_PAYLOAD_BYTES].
 *
 * @param shortId 4-character stable device identifier persisted in DataStore.
 * @param nick    Rider display name or handle from the broadcast profile.
 * @param bike    Motorcycle name from the currently active bike.
 */
data class WavePayload(
    val shortId: String,
    val nick: String,
    val bike: String,
)
