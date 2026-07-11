package com.mototracker.ui.screens.settings

/**
 * Sealed result returned by [BikeFormValidation.validate].
 */
sealed class BikeFormResult {
    /** All fields are valid; use the sanitised values to create or update a bike. */
    data class Valid(val name: String, val year: Int, val plate: String) : BikeFormResult()

    /** The trimmed name field is blank. */
    object NameBlank : BikeFormResult()

    /** The year text is not a number, or falls outside the acceptable 1900–2030 range. */
    object YearInvalid : BikeFormResult()
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
     * @param name     Display name typed by the user (leading/trailing whitespace is trimmed).
     * @param yearText Year typed by the user (may be non-numeric or out of range).
     * @param plate    Registration plate text (trimmed; may be blank — that is allowed).
     * @return [BikeFormResult.Valid] with sanitised values on success, or an error subtype.
     */
    fun validate(name: String, yearText: String, plate: String): BikeFormResult {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return BikeFormResult.NameBlank
        val year = yearText.trim().toIntOrNull()
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) return BikeFormResult.YearInvalid
        return BikeFormResult.Valid(name = trimmedName, year = year, plate = plate.trim())
    }
}
