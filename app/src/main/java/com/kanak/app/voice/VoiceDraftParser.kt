package com.kanak.app.voice

import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import java.math.BigDecimal

data class VoiceDraft(
    val amountText: String,
    val category: ExpenseCategory?,
    val note: String,
    val isAmbiguous: Boolean = false,
    val hasUncertainArithmetic: Boolean = false,
)

/**
 * Utility for parsing spoken transcripts into editable draft fields (amount, category, note)
 * using deterministic local keyword classification and shared mathematical expression parsing.
 */
object VoiceDraftParser {

    /**
     * Infer category from transcript/description with priority precedence.
     */
    fun inferCategory(text: String?, customCategories: List<ExpenseCategory> = emptyList()): ExpenseCategory? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()

        // 0. Girlfriend & Swathi personal keyword intent (HIGHEST precedence when clearly applicable)
        val girlfriendKeywords = listOf(
            "girlfriend", "gf", "date with girlfriend", "gift for girlfriend",
            "flowers for my girlfriend", "flowers for girlfriend", "movie date with girlfriend",
            "girlfriend's birthday", "girl friend", "date with gf", "gift for gf",
            "swathi", "swati", "swathy", "swathi's", "swati's", "swathy's",
            "for swathi", "for swati", "with swathi", "with swati", "swathi's birthday"
        )
        if (girlfriendKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.GIRLFRIEND
        }

        // 1. Food & Dining specific dishes & dining terms
        val foodKeywords = listOf(
            "biryani", "chicken biryani", "mutton biryani", "hyderabadi biryani", "chicken", "mutton", "fish", "prawns",
            "paneer", "dosa", "idli", "vada", "sambar", "roti", "naan", "paratha", "thali", "meals", "meal",
            "pizza", "burger", "sandwich", "pasta", "chowmein", "noodles", "momos", "shawarma",
            "restaurant", "restaurant bill", "hotel", "swiggy", "zomato", "cafe", "dhaba", "dining", "dinner", "lunch",
            "breakfast", "tea", "coffee", "snack", "snacks", "bakery", "cake", "ice cream", "dessert", "food"
        )
        if (foodKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.FOOD
        }

        // 2. Groceries (raw food items, ingredients, staples)
        val groceryKeywords = listOf(
            "vegetables", "veggies", "carrots", "carrot", "tomato", "tomatoes", "onion", "onions", "potato", "potatoes",
            "rice", "wheat", "atta", "dal", "pulses", "milk", "curd", "butter", "cheese", "eggs", "egg",
            "oil", "cooking oil", "ghee", "spices", "masala", "salt", "sugar", "groceries", "grocery", "grocery shopping",
            "supermarket", "jiomart", "bigbasket", "blinkit", "zepto", "instamart", "fruits", "apple", "banana"
        )
        if (groceryKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.GROCERIES
        }

        // 3. Transport (commute, travel, vehicles, fuel)
        val transportKeywords = listOf(
            "bus", "bus ticket", "petrol", "diesel", "fuel", "gasoline", "taxi", "cab", "uber", "ola", "rapido",
            "metro", "subway", "train", "flight", "auto", "rickshaw", "parking", "parking fee", "toll", "fastag",
            "bike service", "car service", "puncture", "mechanic", "bmtc"
        )
        if (transportKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.TRANSPORT
        }

        // 4. Bills & Utilities (household bills, subscriptions, recharges)
        val billKeywords = listOf(
            "electricity", "electricity bill", "power bill", "current bill", "water bill", "gas cylinder",
            "internet", "wifi", "broadband", "recharge", "mobile recharge", "postpaid", "prepaid", "phone bill",
            "rent", "house rent", "maintenance", "utility", "utility bill", "dth", "tata play"
        )
        if (billKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.BILL
        }

        // 5. Health & Medical
        val healthKeywords = listOf(
            "doctor", "doctor fee", "consultation", "medicine", "medicines", "pharmacy", "chemist", "apollo",
            "hospital", "clinic", "lab test", "blood test", "health", "medical"
        )
        if (healthKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.HEALTH
        }

        // 6. Shopping (apparel, footwear, electronics)
        val shoppingKeywords = listOf(
            "clothes", "clothing", "shirt", "pants", "tshirt", "t-shirt", "shoes", "dress", "saree", "footwear",
            "electronics", "mobile", "laptop", "amazon", "flipkart", "myntra", "mall", "shopping"
        )
        if (shoppingKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.SHOPPING
        }

        // 7. Entertainment (movies, shows, gaming)
        val entertainmentKeywords = listOf(
            "movie", "movies", "cinema", "pvr", "inox", "theatre", "concert", "show", "event", "game", "gaming",
            "netflix", "prime", "hotstar", "spotify", "ticket"
        )
        if (entertainmentKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.ENTERTAINMENT
        }

        // 8. Education (tuition, courses, books)
        val educationKeywords = listOf(
            "school", "college", "tuition", "tuition fee", "school fee", "college fee", "books", "book",
            "stationery", "course", "udemy", "exam fee"
        )
        if (educationKeywords.any { lower.contains(it) }) {
            return ExpenseCategory.EDUCATION
        }

        val customMatches = customCategories.filter { category ->
            Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(category.displayName) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
        return customMatches.singleOrNull()
    }

    private const val NUMBER = """\d+(?:[.,]\d+)*|[.,]\d+"""
    private val amountStart = Regex("""\(*\s*(?:$NUMBER)""")
    private val amountSpan = Regex("""[\d.,+\-*/×÷()\s]+""")
    private val multiply = Regex("""(?<=[\d)])\s*x(?:\s+|(?=[\d(.,]))""")
    private val currency = Regex("""[₹$€£]|\b(?:rupees?|rs|dollars?|euros?|pounds?)\b\.?""")
    private val itemBeforeAddition = Regex("""(?<=\d)\s+(?:on|for)\s+([^\d+]+?)\s+and another\s+(?=\d)""")
    private val itemAfterAddition = Regex("""(?:$NUMBER)\s+(?:on|for)\s+(.+)""")
    private val separateItems = Regex("""(?:$NUMBER)\s+(?:on|for)\s+([^\d]+?)\s+and\s+(?:$NUMBER)\s+(?:on|for)\s+(.+)""")
    private val spokenOperators = mapOf(
        Regex("""\b(?:plus(?: another)?|and another|added to|add)\b""") to "+",
        Regex("""\b(?:minus|subtract|subtracted by)\b""") to "-",
        Regex("""\b(?:times|multiply by|multiplied by)\b""") to "*",
        Regex("""\b(?:divide by|divided by)\b""") to "/",
        Regex("""(?<=\d)\s+and\s+(?=[\d(])""") to "+",
        Regex("""\binto\s+(?=[\d(])""") to "*",
        Regex("""\bover\s+(?=[\d(])""") to "/",
        Regex("""\bless\s+(?=[\d(])""") to "-"
    )

    fun parseTranscript(
        transcript: String,
        customCategories: List<ExpenseCategory> = emptyList(),
        currencyCode: String = "INR"
    ): VoiceDraft {
        val note = transcript.trim()
        val draft = VoiceDraft("", inferCategory(note, customCategories), note)
        var normalized = currency.replace(note.lowercase(), " ").replace('−', '-')
        val separate = separateItems.find(normalized)
        if (separate != null) {
            if (separate.groupValues[1].trim() != separate.groupValues[2].trim()) {
                return draft.copy(isAmbiguous = true)
            }
            val amounts = Regex(NUMBER).findAll(separate.value).take(2).map { it.value }.toList()
            normalized = amounts.joinToString(" + ")
        }

        val firstItem = itemBeforeAddition.find(normalized)
        if (firstItem != null) {
            val secondItem = itemAfterAddition.find(normalized, firstItem.range.last + 1)
            if (secondItem != null && firstItem.groupValues[1].trim() != secondItem.groupValues[1].trim()) {
                return draft.copy(isAmbiguous = true)
            }
            normalized = normalized.replaceRange(firstItem.range, " + ")
        }
        for ((phrase, operator) in spokenOperators) {
            normalized = phrase.replace(normalized, " $operator ")
        }
        normalized = multiply.replace(normalized, "*")

        val start = amountStart.find(normalized)?.range?.first ?: return draft
        val expression = amountSpan.find(normalized, start)?.value?.trim()?.trimEnd(',', '.') ?: return draft
        if (MathExpressionParser.hasOperator(expression)) {
            val minor = Expense.parseToMinorUnits(expression, currencyCode)
            if (minor <= 0L) return draft.copy(hasUncertainArithmetic = true)
            val total = BigDecimal.valueOf(minor, Expense.getFractionDigits(currencyCode))
            return draft.copy(amountText = MathExpressionParser.formatBigDecimal(total))
        }

        // Two adjacent numbers with a missing operator need review, even if the recognizer
        // returned the rest of the transcript correctly.
        if (expression.any { it.isWhitespace() }) return draft.copy(hasUncertainArithmetic = true)
        val amount = if (',' in expression) MathExpressionParser.evaluate(expression) else expression
        return draft.copy(amountText = amount.orEmpty())
    }
}
