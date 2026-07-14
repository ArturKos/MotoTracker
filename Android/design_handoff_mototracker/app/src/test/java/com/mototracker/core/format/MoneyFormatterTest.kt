package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `format typical value with PLN`() {
        assertEquals("42.50 PLN", MoneyFormatter.format(42.5, "PLN"))
    }

    @Test
    fun `format zero amount`() {
        assertEquals("0.00 EUR", MoneyFormatter.format(0.0, "EUR"))
    }

    @Test
    fun `format with USD currency code`() {
        assertEquals("123.45 USD", MoneyFormatter.format(123.45, "USD"))
    }

    @Test
    fun `format large amount rounds to two decimals`() {
        assertEquals("1000.00 PLN", MoneyFormatter.format(1000.0, "PLN"))
    }

    @Test
    fun `format fractional amount truncated to two decimals`() {
        // String.format rounds half-up
        assertEquals("10.01 CZK", MoneyFormatter.format(10.009, "CZK"))
    }

    @Test
    fun `format with custom currency code`() {
        assertEquals("5.99 RUB", MoneyFormatter.format(5.99, "RUB"))
    }
}
