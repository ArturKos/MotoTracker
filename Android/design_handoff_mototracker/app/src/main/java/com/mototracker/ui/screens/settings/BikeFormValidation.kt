package com.mototracker.ui.screens.settings

/**
 * Sealed result returned by [BikeFormValidation.validate].
 */
sealed class BikeFormResult {
    /**
     * All fields are valid; use the sanitised values to create or update a bike.
     *
     * Fuel fields ([tankCapacityL], [fuelPricePerL], [consumptionLper100km]) are null when
     * the user left the corresponding text field blank (meaning "unknown").
     *
     * @param autoUpdateConsumption Passed through unchanged from the checkbox state (K2).
     */
    data class Valid(
        val name: String,
        val year: Int,
        val plate: String,
        val tankCapacityL: Double? = null,
        val fuelPricePerL: Double? = null,
        val consumptionLper100km: Double? = null,
        val autoUpdateConsumption: Boolean = false,
    ) : BikeFormResult()

    /** The trimmed name field is blank. */
    object NameBlank : BikeFormResult()

    /** The year text is not a number, or falls outside the acceptable 1900–2030 range. */
    object YearInvalid : BikeFormResult()

    /** Tank capacity field is non-blank but cannot be parsed as a non-negative Double. */
    object TankCapacityInvalid : BikeFormResult()

    /** Fuel price field is non-blank but cannot be parsed as a non-negative Double. */
    object FuelPriceInvalid : BikeFormResult()

    /** Consumption field is non-blank but cannot be parsed as a non-negative Double. */
    object ConsumptionInvalid : BikeFormResult()
}

/**
 * Pure, Android-free validator for the add/edit-motorcycle form.
 *
 * All logic is stateless and has no Android dependencies so it can be exercised
 * with plain JUnit without instrumentation.
 */
object BikeFormValidation {

    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2030

    /**
     * Validates the raw text fields entered in the add/edit motorcycle dialog.
     *
     * Fuel fields ([tankCapacityLText], [fuelPricePerLText], [consumptionLper100kmText]) are
     * optional: a blank or whitespace-only value is treated as null (unknown/not set).
     * A non-blank value must parse as a non-negative Double, or the corresponding
     * [BikeFormResult] error is returned.
     *
     * @param name                     Display name typed by the user (leading/trailing whitespace is trimmed).
     * @param yearText                 Year typed by the user (may be non-numeric or out of range).
     * @param plate                    Registration plate text (trimmed; may be blank — that is allowed).
     * @param tankCapacityLText        Fuel tank capacity in litres; blank → null.
     * @param fuelPricePerLText        Fuel price per litre; blank → null.
     * @param consumptionLper100kmText Average consumption in L/100km; blank → null.
     * @param autoUpdateConsumption    Checkbox state — passed straight through into [BikeFormResult.Valid] (K2).
     * @return [BikeFormResult.Valid] with sanitised values on success, or an error subtype.
     */
    fun validate(
        name: String,
        yearText: String,
        plate: String,
        tankCapacityLText: String = "",
        fuelPricePerLText: String = "",
        consumptionLper100kmText: String = "",
        autoUpdateConsumption: Boolean = false,
    ): BikeFormResult {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return BikeFormResult.NameBlank
        val year = yearText.trim().toIntOrNull()
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) return BikeFormResult.YearInvalid

        val tankCapacityL = parseOptionalNonNegativeDouble(tankCapacityLText)
            ?: return BikeFormResult.TankCapacityInvalid
        val fuelPricePerL = parseOptionalNonNegativeDouble(fuelPricePerLText)
            ?: return BikeFormResult.FuelPriceInvalid
        val consumptionLper100km = parseOptionalNonNegativeDouble(consumptionLper100kmText)
            ?: return BikeFormResult.ConsumptionInvalid

        return BikeFormResult.Valid(
            name = trimmedName,
            year = year,
            plate = plate.trim(),
            tankCapacityL = tankCapacityL.value,
            fuelPricePerL = fuelPricePerL.value,
            consumptionLper100km = consumptionLper100km.value,
            autoUpdateConsumption = autoUpdateConsumption,
        )
    }

    /**
     * Parses an optional Double field: blank → `null` (a special sentinel `Result.success(null)`
     * expressed as a boxed value); non-blank → non-negative Double or returns `null` on failure.
     *
     * Returns a [Result] wrapper:
     * - `Result.success(null)` when blank (no value entered — allowed).
     * - `Result.success(value)` when the text parses as a non-negative Double.
     * - `null` when the text is non-blank but invalid (caller should return the matching error).
     */
    private fun parseOptionalNonNegativeDouble(text: String): DoubleResult? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return DoubleResult(null)
        val value = trimmed.toDoubleOrNull() ?: return null
        if (value < 0.0) return null
        return DoubleResult(value)
    }
}

/** Tiny wrapper so `null` means "parse error" and `DoubleResult(null)` means "blank / not set". */
@JvmInline
private value class DoubleResult(val value: Double?)
