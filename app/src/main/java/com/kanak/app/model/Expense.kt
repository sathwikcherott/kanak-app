package com.kanak.app.model

import com.kanak.app.voice.MathExpressionParser
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/**
 * Core domain model representing a single local expense.
 *
 * @param id Unique database identifier (0 for new items)
 * @param amountMinor Integer amount in minor units (e.g., 1250 cents = ₹12.50 / $12.50)
 * @param currency ISO 4217 currency code (default "INR")
 * @param occurredAt Timestamp in milliseconds when expense occurred
 * @param note Optional text note describing the expense
 * @param category Nullable expense category
 * @param source Entry source (TYPED or VOICE)
 * @param categorizationState Categorization status (CONFIRMED, UNCATEGORIZED, AUTO_CATEGORIZED, PENDING_REVIEW)
 */
data class Expense(
    val id: Long = 0L,
    val amountMinor: Long,
    val currency: String = "INR",
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String? = null,
    val category: ExpenseCategory? = null,
    val source: EntrySource = EntrySource.TYPED,
    val categorizationState: CategorizationState = if (category != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED
) {
    /**
     * Converts minor integer units to formatted decimal string using integer arithmetic (avoiding Float/Double).
     * Applies Indian numbering system grouping (lakhs, crores) for INR currency.
     */
    fun amountAsFormattedString(): String {
        val fractionDigits = getFractionDigits(currency)
        if (fractionDigits == 0) return amountMinor.toString()
        val multiplier = getMultiplier(fractionDigits)
        val major = amountMinor / multiplier
        val minor = kotlin.math.abs(amountMinor % multiplier)
        val formattedMajor = if (currency == "INR") {
            formatIndianGrouping(major)
        } else {
            String.format(Locale.US, "%,d", major)
        }
        return String.format(Locale.US, "%s.%0${fractionDigits}d", formattedMajor, minor)
    }

    /**
     * Formats amount with currency symbol (e.g. "₹1,00,000.00").
     */
    fun amountWithSymbol(): String {
        val symbol = getCurrencySymbol(currency)
        return "$symbol${amountAsFormattedString()}"
    }

    companion object {
        fun getCurrencySymbol(currencyCode: String): String {
            return when (currencyCode) {
                "INR" -> "₹"
                "USD" -> "$"
                "EUR" -> "€"
                "GBP" -> "£"
                else -> try {
                    Currency.getInstance(currencyCode).getSymbol(Locale.forLanguageTag("en-IN"))
                } catch (_: Exception) {
                    "$currencyCode "
                }
            }
        }

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
         * Indian number grouping system:
         * Rightmost 3 digits grouped, subsequent digits grouped in pairs of 2.
         * Example: 1000 -> 1,000 | 100000 -> 1,00,000 | 1000000 -> 10,00,000
         */
        fun formatIndianGrouping(number: Long): String {
            val str = number.toString()
            if (str.length <= 3) return str
            val last3 = str.takeLast(3)
            var rest = str.dropLast(3)
            val result = StringBuilder()
            while (rest.length > 2) {
                val chunk = rest.takeLast(2)
                result.insert(0, ",$chunk")
                rest = rest.dropLast(2)
            }
            result.insert(0, rest)
            result.append(",$last3")
            return result.toString()
        }

        /**
         * Converts user input string into integer minor units using integer arithmetic only without Float/Double.
         * Supports grouping commas in input. Default currencyCode is "INR".
         * Evaluates mathematical expressions using deterministic MathExpressionParser if input contains math operators.
         */
        fun parseToMinorUnits(input: String, currencyCode: String = "INR"): Long {
            var cleaned = input.trim()
            if (cleaned.isEmpty()) return 0L

            if (MathExpressionParser.hasOperator(cleaned)) {
                val amount = MathExpressionParser.evaluateToBigDecimal(cleaned) ?: return 0L
                return try {
                    amount.movePointRight(getFractionDigits(currencyCode))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact()
                } catch (_: ArithmeticException) {
                    0L
                }
            }

            // Handle grouping commas in input (e.g., "1,00,000.00" or "1,000.00")
            if (cleaned.contains(",") && cleaned.contains(".")) {
                cleaned = cleaned.replace(",", "")
            } else if (cleaned.count { it == ',' } > 1) {
                cleaned = cleaned.replace(",", "")
            } else if (cleaned.contains(",")) {
                cleaned = cleaned.replace(",", ".")
            }

            // Reject multiple decimal separators or non-digit/non-dot characters
            if (cleaned.count { it == '.' } > 1) return 0L
            if (!cleaned.all { it.isDigit() || (it == '.') }) return 0L

            val fractionDigits = getFractionDigits(currencyCode)
            val parts = cleaned.split(".")

            val majorStr = parts[0]
            val minorStr = parts.getOrNull(1)

            // Reject if fractional digits exceed currency fraction digits
            if ((minorStr != null) && (minorStr.length > fractionDigits)) {
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
