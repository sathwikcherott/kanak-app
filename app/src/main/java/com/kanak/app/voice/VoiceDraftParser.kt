package com.kanak.app.voice

import com.kanak.app.model.ExpenseCategory

data class VoiceDraft(
    val amountText: String,
    val category: ExpenseCategory?,
    val note: String
)

/**
 * Utility for parsing spoken transcripts into editable draft fields (amount, category, note).
 */
object VoiceDraftParser {

    fun parseTranscript(transcript: String): VoiceDraft {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return VoiceDraft(amountText = "", category = null, note = "")

        // Regex for capturing numbers (e.g., "$25.50", "100 dollars", "15.00", "45")
        val amountRegex = Regex("""[$€£₹]?\s*(\d+(?:[.,]\d{1,2})?)""")
        val match = amountRegex.find(trimmed)
        val amountText = match?.groupValues?.get(1)?.replace(",", ".") ?: ""

        // Infer expense category from common spoken keywords
        val lower = trimmed.lowercase()
        val category = when {
            lower.contains("food") || lower.contains("lunch") || lower.contains("dinner") ||
                    lower.contains("breakfast") || lower.contains("coffee") || lower.contains("restaurant") -> ExpenseCategory.FOOD

            lower.contains("taxi") || lower.contains("uber") || lower.contains("bus") ||
                    lower.contains("metro") || lower.contains("cab") || lower.contains("train") ||
                    lower.contains("fuel") || lower.contains("gas") -> ExpenseCategory.TRANSPORT

            lower.contains("shopping") || lower.contains("clothes") || lower.contains("target") ||
                    lower.contains("amazon") || lower.contains("shoes") -> ExpenseCategory.SHOPPING

            lower.contains("bill") || lower.contains("electricity") || lower.contains("wifi") ||
                    lower.contains("rent") || lower.contains("utility") || lower.contains("phone") -> ExpenseCategory.BILL

            lower.contains("doctor") || lower.contains("pharmacy") || lower.contains("medicine") ||
                    lower.contains("hospital") || lower.contains("health") -> ExpenseCategory.HEALTH

            lower.contains("movie") || lower.contains("game") || lower.contains("concert") ||
                    lower.contains("ticket") -> ExpenseCategory.ENTERTAINMENT

            else -> null
        }

        return VoiceDraft(
            amountText = amountText,
            category = category,
            note = trimmed
        )
    }
}
