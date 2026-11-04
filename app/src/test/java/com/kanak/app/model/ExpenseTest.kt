package com.kanak.app.model

import com.kanak.app.data.local.entity.ExpenseEntity
import com.kanak.app.data.local.entity.toDomainModel
import com.kanak.app.data.local.entity.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseTest {

    @Test
    fun defaultCurrency_isINR() {
        val expense = Expense(amountMinor = 10000L)
        assertEquals("INR", expense.currency)
    }

    @Test
    fun girlfriendCategory_serializationAndDeserialization() {
        val expense = Expense(
            amountMinor = 150000L,
            currency = "INR",
            note = "Flowers for girlfriend",
            category = ExpenseCategory.GIRLFRIEND
        )
        val entity = expense.toEntity()
        assertEquals("GIRLFRIEND", entity.category)

        val domain = entity.toDomainModel()
        assertEquals(ExpenseCategory.GIRLFRIEND, domain.category)
        assertEquals("Girlfriend", domain.category?.displayName)
    }

    @Test
    fun legacyOrUnknownCategory_handledSafelyWithoutErrors() {
        val entity = ExpenseEntity(
            id = 1L,
            amountMinor = 1000L,
            currency = "INR",
            occurredAt = 100000L,
            note = "Old item",
            category = "UNKNOWN_LEGACY_CATEGORY_XYZ",
            source = "TYPED",
            categorizationState = "UNCATEGORIZED"
        )
        val domain = entity.toDomainModel()
        assertNull(domain.category)
    }

    @Test
    fun parseToMinorUnits_defaultsToINR() {
        val minor = Expense.parseToMinorUnits("250.50")
        assertEquals(25050L, minor)
    }

    @Test
    fun parseToMinorUnits_handlesIndianNumberGroupingCommas() {
        // 1,000.00 INR -> 100000 minor units
        assertEquals(100000L, Expense.parseToMinorUnits("1,000.00", "INR"))

        // 25,500.50 INR -> 2550050 minor units
        assertEquals(2550050L, Expense.parseToMinorUnits("25,500.50", "INR"))

        // 1,00,000.00 INR (1 Lakh) -> 10000000 minor units
        assertEquals(10000000L, Expense.parseToMinorUnits("1,00,000.00", "INR"))

        // 10,00,000.00 INR (10 Lakhs) -> 100000000 minor units
        assertEquals(100000000L, Expense.parseToMinorUnits("10,00,000.00", "INR"))
    }

    @Test
    fun amountAsFormattedString_appliesIndianNumberGroupingForINR() {
        // ₹1,000.00
        val exp1 = Expense(amountMinor = 100000L, currency = "INR")
        assertEquals("1,000.00", exp1.amountAsFormattedString())
        assertEquals("₹1,000.00", exp1.amountWithSymbol())

        // ₹25,500.50
        val exp2 = Expense(amountMinor = 2550050L, currency = "INR")
        assertEquals("25,500.50", exp2.amountAsFormattedString())
        assertEquals("₹25,500.50", exp2.amountWithSymbol())

        // ₹1,00,000.00
        val exp3 = Expense(amountMinor = 10000000L, currency = "INR")
        assertEquals("1,00,000.00", exp3.amountAsFormattedString())
        assertEquals("₹1,00,000.00", exp3.amountWithSymbol())

        // ₹10,00,000.00
        val exp4 = Expense(amountMinor = 100000000L, currency = "INR")
        assertEquals("10,00,000.00", exp4.amountAsFormattedString())
        assertEquals("₹10,00,000.00", exp4.amountWithSymbol())
    }

    @Test
    fun USD_and_other_currencies_remain_functional() {
        // USD (2 decimals)
        val usdExpense = Expense(amountMinor = 25050L, currency = "USD")
        assertEquals("250.50", usdExpense.amountAsFormattedString())
        assertEquals("$250.50", usdExpense.amountWithSymbol())

        // JPY (0 decimals)
        val jpyExpense = Expense(amountMinor = 250L, currency = "JPY")
        assertEquals("250", jpyExpense.amountAsFormattedString())

        // KWD (3 decimals)
        val kwdExpense = Expense(amountMinor = 250999L, currency = "KWD")
        assertEquals("250.999", kwdExpense.amountAsFormattedString())
    }

    @Test
    fun parseToMinorUnits_250_convertsToMinorUnits() {
        val minor = Expense.parseToMinorUnits("250", "USD")
        assertEquals(25000L, minor)
    }

    @Test
    fun parseToMinorUnits_rejectsMultipleDecimalSeparators() {
        assertEquals(0L, Expense.parseToMinorUnits("250..50", "INR"))
    }

    @Test
    fun parseToMinorUnits_rejectsNegativeAndZeroAmounts() {
        assertEquals(0L, Expense.parseToMinorUnits("-250", "INR"))
        assertEquals(0L, Expense.parseToMinorUnits("0", "INR"))
    }

    @Test
    fun parseToMinorUnits_preventsLongOverflowOnVeryLargeValues() {
        assertEquals(0L, Expense.parseToMinorUnits("99999999999999999999999999999", "INR"))
    }
}
