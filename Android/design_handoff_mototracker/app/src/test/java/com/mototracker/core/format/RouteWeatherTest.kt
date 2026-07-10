package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [RouteWeather.parse]. */
class RouteWeatherTest {

    // ── offline conditions ────────────────────────────────────────────────────

    @Test
    fun `null wxJson returns offline WeatherUi`() {
        val result = RouteWeather.parse(null)
        assertTrue(result.offline)
        assertEquals("—", result.tempDisplay)
        assertEquals("—", result.humDisplay)
        assertEquals("—", result.rainLabel)
    }

    @Test
    fun `blank wxJson returns offline WeatherUi`() {
        assertTrue(RouteWeather.parse("").offline)
        assertTrue(RouteWeather.parse("   ").offline)
    }

    @Test
    fun `malformed JSON returns offline WeatherUi`() {
        assertTrue(RouteWeather.parse("not json").offline)
        assertTrue(RouteWeather.parse("hello world!!!").offline)
        assertTrue(RouteWeather.parse("[]").offline)
    }

    @Test
    fun `null temp field returns offline WeatherUi`() {
        val result = RouteWeather.parse("""{"temp":null,"hum":60,"rain":false}""")
        assertTrue(result.offline)
    }

    @Test
    fun `missing temp field returns offline WeatherUi`() {
        val result = RouteWeather.parse("""{"hum":60,"rain":false}""")
        assertTrue(result.offline)
    }

    // ── online conditions ─────────────────────────────────────────────────────

    @Test
    fun `valid JSON with all fields returns online WeatherUi`() {
        val result = RouteWeather.parse("""{"temp":22,"hum":60,"rain":false}""")
        assertFalse(result.offline)
        assertEquals("22°C", result.tempDisplay)
        assertEquals("60%", result.humDisplay)
        assertEquals("No rain", result.rainLabel)
    }

    @Test
    fun `rain=true sets Rain label`() {
        val result = RouteWeather.parse("""{"temp":15,"hum":85,"rain":true}""")
        assertFalse(result.offline)
        assertEquals("Rain", result.rainLabel)
    }

    @Test
    fun `rain=false sets No rain label`() {
        val result = RouteWeather.parse("""{"temp":20,"hum":50,"rain":false}""")
        assertEquals("No rain", result.rainLabel)
    }

    @Test
    fun `null hum field shows dash for humidity`() {
        val result = RouteWeather.parse("""{"temp":18,"hum":null,"rain":false}""")
        assertFalse(result.offline)
        assertEquals("—", result.humDisplay)
    }

    @Test
    fun `missing hum field shows dash for humidity`() {
        val result = RouteWeather.parse("""{"temp":18,"rain":false}""")
        assertFalse(result.offline)
        assertEquals("—", result.humDisplay)
    }

    @Test
    fun `negative temperature is formatted correctly`() {
        val result = RouteWeather.parse("""{"temp":-5,"hum":90,"rain":true}""")
        assertFalse(result.offline)
        assertEquals("-5°C", result.tempDisplay)
    }

    @Test
    fun `zero temperature is formatted correctly`() {
        val result = RouteWeather.parse("""{"temp":0,"hum":70,"rain":false}""")
        assertFalse(result.offline)
        assertEquals("0°C", result.tempDisplay)
    }

    @Test
    fun `humidity at boundary values is formatted correctly`() {
        assertEquals("0%", RouteWeather.parse("""{"temp":20,"hum":0,"rain":false}""").humDisplay)
        assertEquals("100%", RouteWeather.parse("""{"temp":20,"hum":100,"rain":false}""").humDisplay)
    }
}
