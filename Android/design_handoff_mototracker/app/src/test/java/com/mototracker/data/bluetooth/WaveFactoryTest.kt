package com.mototracker.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaveFactoryTest {

    private fun rider(
        shortId: String = "AB12",
        nick: String = "Rider",
        bike: String = "MT-07",
        rssiDbm: Int = -65,
        elapsedRealtimeMs: Long = 12_345L,
    ) = DiscoveredRider(
        shortId = shortId,
        nick = nick,
        bike = bike,
        rssiDbm = rssiDbm,
        elapsedRealtimeMs = elapsedRealtimeMs,
    )

    @Test
    fun `id is mapped to Wave id`() {
        val wave = WaveFactory.toWave(rider(), "Wawel", "14:32", "route-1", "wave-uuid-42")
        assertEquals("wave-uuid-42", wave.id)
    }

    @Test
    fun `rider nick is mapped to Wave nick`() {
        val wave = WaveFactory.toWave(rider(nick = "ZbyszekMoto"), "Rynek", "09:00", null, "w1")
        assertEquals("ZbyszekMoto", wave.nick)
    }

    @Test
    fun `rider bike is mapped to Wave bikeName`() {
        val wave = WaveFactory.toWave(rider(bike = "Honda CB500F"), "Rynek", "09:00", null, "w1")
        assertEquals("Honda CB500F", wave.bikeName)
    }

    @Test
    fun `place is mapped to Wave place`() {
        val wave = WaveFactory.toWave(rider(), "Kraków centrum", "12:00", null, "w2")
        assertEquals("Kraków centrum", wave.place)
    }

    @Test
    fun `timeLabel is mapped to Wave timeLabel`() {
        val wave = WaveFactory.toWave(rider(), "", "17:45", null, "w3")
        assertEquals("17:45", wave.timeLabel)
    }

    @Test
    fun `non-null routeId is preserved`() {
        val wave = WaveFactory.toWave(rider(), "", "10:00", "route-abc", "w4")
        assertEquals("route-abc", wave.routeId)
    }

    @Test
    fun `null routeId is preserved`() {
        val wave = WaveFactory.toWave(rider(), "", "10:00", null, "w5")
        assertNull(wave.routeId)
    }

    @Test
    fun `empty place and time produce valid Wave`() {
        val wave = WaveFactory.toWave(rider(), "", "", null, "w6")
        assertEquals("", wave.place)
        assertEquals("", wave.timeLabel)
    }

    @Test
    fun `rssi and elapsedRealtimeMs are not present in Wave`() {
        // Wave model has no signal-strength or timestamp field; verify the mapping doesn't crash
        val wave = WaveFactory.toWave(
            rider(rssiDbm = -90, elapsedRealtimeMs = 9_999_999L),
            "Test", "00:00", null, "w7"
        )
        assertEquals("Rider", wave.nick)
    }

    @Test
    fun `all fields map in one complete call`() {
        val r = rider(shortId = "ZZ88", nick = "Piotr", bike = "Ducati 916", rssiDbm = -55)
        val wave = WaveFactory.toWave(r, "Zakopane", "20:15", "route-xyz", "full-uuid")
        assertEquals("full-uuid", wave.id)
        assertEquals("Piotr", wave.nick)
        assertEquals("Ducati 916", wave.bikeName)
        assertEquals("Zakopane", wave.place)
        assertEquals("20:15", wave.timeLabel)
        assertEquals("route-xyz", wave.routeId)
    }
}
