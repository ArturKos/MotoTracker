package com.mototracker.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BikeFormValidation].
 *
 * Pure JUnit — no Android runtime or Robolectric required.
 */
class BikeFormValidationTest {

    private fun valid(
        name: String = "Yamaha MT-07",
        yearText: String = "2020",
        plate: String = "WA 1234",
        tankText: String = "",
        priceText: String = "",
        consumptionText: String = "",
        autoUpdate: Boolean = false,
    ) = BikeFormValidation.validate(name, yearText, plate, tankText, priceText, consumptionText, autoUpdate)

    // ── Basic happy-path ─────────────────────────────────────────────────────

    @Test
    fun `valid form returns Valid`() {
        assertTrue(valid() is BikeFormResult.Valid)
    }

    @Test
    fun `blank name returns NameBlank`() {
        assertTrue(valid(name = "  ") is BikeFormResult.NameBlank)
    }

    @Test
    fun `non-numeric year returns YearInvalid`() {
        assertTrue(valid(yearText = "abc") is BikeFormResult.YearInvalid)
    }

    @Test
    fun `year below 1900 returns YearInvalid`() {
        assertTrue(valid(yearText = "1899") is BikeFormResult.YearInvalid)
    }

    @Test
    fun `year above 2030 returns YearInvalid`() {
        assertTrue(valid(yearText = "2031") is BikeFormResult.YearInvalid)
    }

    @Test
    fun `negative tank capacity returns TankCapacityInvalid`() {
        assertTrue(valid(tankText = "-1") is BikeFormResult.TankCapacityInvalid)
    }

    @Test
    fun `blank fuel fields are allowed and result in null`() {
        val result = valid() as BikeFormResult.Valid
        assertFalse(result.tankCapacityL != null)
        assertFalse(result.fuelPricePerL != null)
        assertFalse(result.consumptionLper100km != null)
    }

    // ── autoUpdateConsumption pass-through (K2) ───────────────────────────────

    @Test
    fun `autoUpdateConsumption false passes through into Valid`() {
        val result = valid(autoUpdate = false) as BikeFormResult.Valid
        assertFalse(result.autoUpdateConsumption)
    }

    @Test
    fun `autoUpdateConsumption true passes through into Valid`() {
        val result = valid(autoUpdate = true) as BikeFormResult.Valid
        assertTrue(result.autoUpdateConsumption)
    }

    @Test
    fun `autoUpdateConsumption does not affect validation outcome`() {
        // A form that would fail validation (blank name) ignores the checkbox
        val result = valid(name = "", autoUpdate = true)
        assertTrue(result is BikeFormResult.NameBlank)
    }

    @Test
    fun `default autoUpdateConsumption is false`() {
        val result = BikeFormValidation.validate("Bike", "2020", "WA 1") as BikeFormResult.Valid
        assertFalse(result.autoUpdateConsumption)
    }
}
