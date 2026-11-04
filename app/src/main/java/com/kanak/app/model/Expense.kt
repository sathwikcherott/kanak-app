package com.kanak.app.model

import java.util.Currency
import java.util.Locale

/**
 * Core domain model representing a single local expense.
 *
 * @param id Unique database identifier (0 for new items)
 * @param amountMinor Integer amount in minor units (e.g., 1250 cents = 12.50 USD / ₹12.50)
 * @param currency ISO 4217 currency code (e.g. "USD", "INR", "EUR")
 * @param occurredAt Timestamp in milliseconds when expense occurred
 * @param note Optional text note describing the expense
 * @param category Nullable expense category
 * @param source Entry source (TYPED or VOICE)
 * @param categorizationState Categorization status (CONFIRMED, UNCATEGORIZED, AUTO_CATEGORIZED, PENDING_REVIEW)
 */
data class Expense(
    val id: Long = 0L,
    val amountMinor: Long,
    val currency: String = "USD",
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String? = null,
    val category: ExpenseCategory? = null,
    val source: EntrySource = EntrySource.TYPED,
    val categorizationState: CategorizationState = if (category != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED
) {
    /**
     * Converts minor integer units to formatted decimal string using integer arithmetic (avoiding Float/Double).
     */
    fun amountAsFormattedString(): String {
        val fractionDigits = getFractionDigits(currency)
        if (fractionDigits == 0) return amountMinor.toString()
        val multiplier = getMultiplier(fractionDigits)
        val major = amountMinor / multiplier
        val minor = kotlin.math.abs(amountMinor % multiplier)
        return String.format(Locale.US, "%d.%0${fractionDigits}d", major, minor)
    }

    companion object {
        fun getFractionDigits(currencyCode: String): Int {
            return try {
                Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
            } catch (_: Exception) {
                2
            }
        }

        fun getMultiplier(fractionDigits: Int): Long {
            var result = 1L
            repeat(fractionDigits) { result *= 10L }
            return result
        }

        /**
         * Converts user input string (e.g., "12.50" or "100") into integer minor units (e.g., 1250 or 10000).
         * Uses integer arithmetic only without Float/Double conversion.
         * Returns 0L for any malformed, negative, zero, overflowed, or extra-fractional-digit inputs.
         */
        fun parseToMinorUnits(input: String, currencyCode: String = "USD"): Long {
            val cleaned = input.trim().replace(",", ".")
            if (cleaned.isEmpty()) return 0L

            // Reject multiple decimal separators or non-digit/non-dot characters
            if (cleaned.count { it == '.' } > 1) return 0L
            if (!cleaned.all { it.isDigit() || (it == '.') }) return 0L

            val fractionDigits = getFractionDigits(currencyCode)
            val parts = cleaned.split(".")

            val majorStr = parts[0]
            val minorStr = parts.getOrNull(1)

            // Reject if fractional digits exceed currency fraction digits
            if (minorStr != null && minorStr.length > fractionDigits) {
                return 0L
            }

            val major = if (majorStr.isEmpty()) 0L else (majorStr.toLongOrNull() ?: return 0L)

            val minor = if (minorStr != null) {
                if (fractionDigits == 0) return 0L
                if (minorStr.isEmpty()) return 0L
                val minorPadded = minorStr.padEnd(fractionDigits, '0')
                minorPadded.toLongOrNull() ?: return 0L
            } else {
                0L
            }

            return try {
                val multiplier = getMultiplier(fractionDigits)
                val majorInMinor = Math.multiplyExact(major, multiplier)
                val totalMinor = Math.addExact(majorInMinor, minor)
                if (totalMinor <= 0L) 0L else totalMinor
            } catch (_: ArithmeticException) {
                0L
            }
        }
    }
}
