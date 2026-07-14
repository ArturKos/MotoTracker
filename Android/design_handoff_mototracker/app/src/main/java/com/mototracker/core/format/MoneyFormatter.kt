package com.mototracker.core.format

import java.util.Locale

/**
 * Pure stateless helper for formatting monetary amounts with a currency code.
 *
 * Has no Android dependencies and is safe to use in unit tests without Robolectric.
 */
object MoneyFormatter {

    /**
     * Formats [amount] to two decimal places followed by [currencyCode].
     *
     * Example: `format(42.5, "PLN")` → `"42.50 PLN"`.
     *
     * @param amount       Monetary value; may be zero.
     * @param currencyCode ISO 4217 currency code or any short label (e.g. "PLN", "EUR", "USD").
     * @return Human-readable string, e.g. `"42.50 PLN"`.
     */
    fun format(amount: Double, currencyCode: String): String =
        String.format(Locale.US, "%.2f %s", amount, currencyCode)
}
