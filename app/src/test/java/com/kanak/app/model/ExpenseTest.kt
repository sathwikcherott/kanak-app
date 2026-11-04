package com.kanak.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseTest {

    @Test
    fun parseToMinorUnits_250_convertsToMinorUnits() {
        // 250 in USD (2 decimals) -> 25000 minor units
        val minor = Expense.parseToMinorUnits("250", "USD")
        assertEquals(25000L, minor)
    }

    @Test
    fun parseToMinorUnits_250_50_convertsToMinorUnits() {
        // 250.50 in USD (2 decimals) -> 25050 minor units
        val minor = Expense.parseToMinorUnits("250.50", "USD")
        assertEquals(25050L, minor)
    }

    @Test
    fun parseToMinorUnits_0_75_convertsToMinorUnits() {
        // 0.75 in USD (2 decimals) -> 75 minor units
        val minor = Expense.parseToMinorUnits("0.75", "USD")
        assertEquals(75L, minor)
    }

    @Test
    fun parseToMinorUnits_250_999_rejectedForTwoDecimalCurrency_andAcceptedForThreeDecimalCurrency() {
        // 250.999 in USD (2 decimals) -> Rejected (0L)
        val usdMinor = Expense.parseToMinorUnits("250.999", "USD")
        assertEquals(0L, usdMinor)

        // 250.999 in KWD (3 decimals) -> 250999 minor units
        val kwdMinor = Expense.parseToMinorUnits("250.999", "KWD")
        assertEquals(250999L, kwdMinor)
    }

    @Test
    fun parseToMinorUnits_rejectsMultipleDecimalSeparators() {
        // 250..50 -> Rejected (0L)
        val minorDoubleDot = Expense.parseToMinorUnits("250..50", "USD")
        assertEquals(0L, minorDoubleDot)

        // 250.50.10 -> Rejected (0L)
        val minorMultipleDot = Expense.parseToMinorUnits("250.50.10", "USD")
        assertEquals(0L, minorMultipleDot)
    }

    @Test
    fun parseToMinorUnits_rejectsNegativeAndZeroAmounts() {
        // -250 -> Rejected (0L)
        assertEquals(0L, Expense.parseToMinorUnits("-250", "USD"))
        // 0 -> Rejected (0L)
        assertEquals(0L, Expense.parseToMinorUnits("0", "USD"))
        // 0.00 -> Rejected (0L)
        assertEquals(0L, Expense.parseToMinorUnits("0.00", "USD"))
    }

    @Test
    fun parseToMinorUnits_rejectsEmptyAndBlankInput() {
        assertEquals(0L, Expense.parseToMinorUnits("", "USD"))
        assertEquals(0L, Expense.parseToMinorUnits("   ", "USD"))
    }

    @Test
    fun parseToMinorUnits_preventsLongOverflowOnVeryLargeValues() {
        // Very large numbers exceeding Long.MAX_VALUE -> Rejected (0L)
        val overflowResult = Expense.parseToMinorUnits("99999999999999999999999999999", "USD")
        assertEquals(0L, overflowResult)

        // Value that overflows when multiplied by 100
        val nearMax = "9223372036854775807"
        val overflowMultiply = Expense.parseToMinorUnits(nearMax, "USD")
        assertEquals(0L, overflowMultiply)
    }

    @Test
    fun parseToMinorUnits_handlesZeroDecimalCurrencies() {
        // JPY (0 fraction digits)
        val jpyValid = Expense.parseToMinorUnits("250", "JPY")
        assertEquals(250L, jpyValid)

        // JPY with decimals -> Rejected (0L)
        val jpyInvalid = Expense.parseToMinorUnits("250.50", "JPY")
        assertEquals(0L, jpyInvalid)
    }

    @Test
    fun parseToMinorUnits_handlesThreeDecimalCurrencies() {
        // BHD (Bahraini Dinar - 3 fraction digits)
        val bhdValid = Expense.parseToMinorUnits("250.500", "BHD")
        assertEquals(250500L, bhdValid)

        val bhdThreeDecimals = Expense.parseToMinorUnits("250.999", "BHD")
        assertEquals(250999L, bhdThreeDecimals)

        // BHD with 4 decimals -> Rejected (0L)
        val bhdInvalid = Expense.parseToMinorUnits("250.9999", "BHD")
        assertEquals(0L, bhdInvalid)
    }

    @Test
    fun amountAsFormattedString_formatsMinorUnitsCorrectly() {
        // USD (2 decimals)
        assertEquals("250.00", Expense(amountMinor = 25000L, currency = "USD").amountAsFormattedString())
        assertEquals("250.50", Expense(amountMinor = 25050L, currency = "USD").amountAsFormattedString())
        assertEquals("0.75", Expense(amountMinor = 75L, currency = "USD").amountAsFormattedString())

        // JPY (0 decimals)
        assertEquals("250", Expense(amountMinor = 250L, currency = "JPY").amountAsFormattedString())

        // KWD (3 decimals)
        assertEquals("250.999", Expense(amountMinor = 250999L, currency = "KWD").amountAsFormattedString())
    }

    @Test
    fun categorizationState_defaultsToConfirmedWhenCategoryPresent() {
        val expense = Expense(amountMinor = 1000L, category = ExpenseCategory.FOOD)
        assertEquals(CategorizationState.CONFIRMED, expense.categorizationState)
    }

    @Test
    fun categorizationState_defaultsToUncategorizedWhenCategoryNull() {
        val expense = Expense(amountMinor = 1000L, category = null)
        assertEquals(CategorizationState.UNCATEGORIZED, expense.categorizationState)
    }
}
