package com.kanak.app.smartinbox

import java.math.BigDecimal
import java.math.RoundingMode

/** Parses an explicitly shared payment text; the original text is never stored. */
object SharedReceiptParser {
    data class Receipt(val amountMinor: Long, val merchant: String)

    private val amountPattern = Regex("(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val paidTerms = Regex("\\b(paid|payment|debited|spent)\\b", RegexOption.IGNORE_CASE)
    private val creditTerms = Regex("\\b(credited|received|refund(?:ed)?|cashback)\\b", RegexOption.IGNORE_CASE)
    private val merchantPatterns = listOf(
        Regex("\\b(?:paid|payment)\\s+(?:of\\s+)?(?:₹|rs\\.?|inr)?\\s*[0-9,.]+\\s+(?:to|at)\\s+([^.;\\n]+)", RegexOption.IGNORE_CASE),
        Regex("\\b(?:to|at)\\s+([^.;\\n]+)", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String): Receipt? {
        if (text.length > 4_096 || creditTerms.containsMatchIn(text) || !paidTerms.containsMatchIn(text)) return null
        val amount = amountPattern.find(text)?.groupValues?.getOrNull(1)?.toMinor() ?: return null
        val merchant = merchantPatterns.asSequence().mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
            .map { it.trim().trim('.', ',', ':', ';', '-') }.firstOrNull { it.length >= 2 } ?: return null
        return Receipt(amount, merchant)
    }

    private fun String.toMinor(): Long? = runCatching {
        BigDecimal(replace(",", "")).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()?.takeIf { it > 0L }
}
