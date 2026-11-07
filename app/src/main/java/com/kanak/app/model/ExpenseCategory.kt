package com.kanak.app.model

import java.text.Normalizer
import java.util.Locale

/** Stable stored identity: original built-in names are unchanged; user categories use UUIDs. */
class ExpenseCategory private constructor(val name: String, val displayName: String) {
    val isCustom: Boolean get() = name.startsWith("custom:")
    override fun equals(other: Any?): Boolean = other is ExpenseCategory && name == other.name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = displayName

    companion object {
        val GIRLFRIEND = ExpenseCategory("GIRLFRIEND", "Girlfriend")
        val FOOD = ExpenseCategory("FOOD", "Food & Dining")
        val GROCERIES = ExpenseCategory("GROCERIES", "Groceries")
        val TRANSPORT = ExpenseCategory("TRANSPORT", "Transport")
        val SHOPPING = ExpenseCategory("SHOPPING", "Shopping")
        val BILL = ExpenseCategory("BILL", "Bills & Utilities")
        val HEALTH = ExpenseCategory("HEALTH", "Health")
        val ENTERTAINMENT = ExpenseCategory("ENTERTAINMENT", "Entertainment")
        val EDUCATION = ExpenseCategory("EDUCATION", "Education")
        val OTHER = ExpenseCategory("OTHER", "Other")
        val entries: List<ExpenseCategory> = listOf(GIRLFRIEND, FOOD, GROCERIES, TRANSPORT, SHOPPING, BILL, HEALTH, ENTERTAINMENT, EDUCATION, OTHER)
        const val MAX_CUSTOM_CATEGORIES = 100
        const val MAX_NAME_LENGTH = 40

        fun valueOf(name: String): ExpenseCategory = entries.find { it.name == name }
            ?: throw IllegalArgumentException("Unknown built-in category: $name")

        fun normalizedName(value: String): String = cleanName(value).lowercase(Locale.ROOT)

        fun cleanName(value: String): String {
            require(value.length <= 200) { "Category name is too long" }
            require(value.none { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }) { "Use a category name without control characters" }
            val cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().replace(Regex("""[\p{Z}\s]+"""), " ")
            require(cleaned.isNotBlank()) { "Enter a category name" }
            require(cleaned.codePointCount(0, cleaned.length) <= MAX_NAME_LENGTH) { "Use $MAX_NAME_LENGTH characters or fewer" }
            return cleaned
        }

        fun isBuiltInName(value: String): Boolean {
            val normalized = normalizedName(value)
            return entries.any { normalizedName(it.displayName) == normalized || normalizedName(it.name) == normalized }
        }

        fun custom(id: String, label: String): ExpenseCategory {
            require(id.matches(Regex("custom:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) { "Invalid custom category ID" }
            val cleaned = cleanName(label)
            require(!isBuiltInName(cleaned)) { "This category already exists" }
            return ExpenseCategory(id, cleaned)
        }
    }
}
