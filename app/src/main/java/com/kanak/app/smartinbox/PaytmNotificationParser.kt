package com.kanak.app.smartinbox

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Conservative, local-only parser for Paytm payment notifications.  A message
 * must explicitly describe a debit; ambiguous and credit/refund messages are
 * deliberately ignored.
 */
object PaytmNotificationParser {
    data class ParsedDebit(val amountMinor: Long, val merchant: String)

    private val amountPattern = Regex("(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val debitTerms = Regex("\\b(paid|debited|spent|payment (?:of|to))\\b", RegexOption.IGNORE_CASE)
    private val creditTerms = Regex("\\b(credited|received|refund(?:ed)?|cashback|added to wallet)\\b", RegexOption.IGNORE_CASE)
    private val merchantPatterns = listOf(
        Regex("\\b(?:paid|payment)\\s+(?:of\\s+)?(?:₹|rs\\.?|inr)?\\s*[0-9,.]+\\s+(?:to|at)\\s+(.+)", RegexOption.IGNORE_CASE),
        Regex("\\b(?:to|at)\\s+([^.;\\n]+)", RegexOption.IGNORE_CASE)
    )

    fun parse(packageName: String, text: String): ParsedDebit? {
        if (packageName != PAYTM_PACKAGE) return null
        if (creditTerms.containsMatchIn(text) || !debitTerms.containsMatchIn(text)) return null
        val amount = amountPattern.find(text)?.groupValues?.getOrNull(1)?.toMinor() ?: return null
        val merchant = merchantPatterns.asSequence()
            .mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
            .map { it.trim().trim('.', ',', ':', ';', '-') }
            .firstOrNull { it.length >= 2 }
            ?: return null
        return ParsedDebit(amount, merchant)
    }

    private fun String.toMinor(): Long? = runCatching {
        BigDecimal(replace(",", "")).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()?.takeIf { it > 0L }

    const val PAYTM_PACKAGE = "net.one97.paytm"
}
