package com.kanak.app.voice

import com.kanak.app.model.ExpenseCategory

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
    fun inferCategory(text: String?): ExpenseCategory? {
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

        return null
    }

    fun parseTranscript(transcript: String): VoiceDraft {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return VoiceDraft(amountText = "", category = null, note = "")

        val category = inferCategory(trimmed)

        // 1. Check for ambiguous multi-transaction inputs (e.g., "25 for lunch and 50 for dinner")
        if (isAmbiguousMultiTransaction(trimmed)) {
            return VoiceDraft(
                amountText = "",
                category = category,
                note = trimmed,
                isAmbiguous = true
            )
        }

        // 2. Speech normalization and math expression extraction
        val (mathExpr, hasConflictingOperators) = extractMathExpression(trimmed)
        if (hasConflictingOperators) {
            return VoiceDraft(
                amountText = "",
                category = category,
                note = trimmed,
                hasUncertainArithmetic = true
            )
        }

        if (mathExpr != null) {
            val evaluated = MathExpressionParser.evaluate(mathExpr)
            return if (evaluated != null) {
                VoiceDraft(
                    amountText = evaluated,
                    category = category,
                    note = trimmed,
                )
            } else {
                VoiceDraft(
                    amountText = "",
                    category = category,
                    note = trimmed,
                    hasUncertainArithmetic = true,
                )
            }
        }

        // 3. Single-number extraction for ordinary voice entries
        val amountRegex = Regex("""[$€£₹]?\s*(\d+(?:[.,]\d{1,2})?)""")
        val match = amountRegex.find(trimmed)
        val amountText = match?.groupValues?.get(1)?.replace(",", ".") ?: ""

        return VoiceDraft(
            amountText = amountText,
            category = category,
            note = trimmed,
        )
    }

    private fun isAmbiguousMultiTransaction(text: String): Boolean {
        val lower = text.lowercase()
        // Matches pattern: "X for item1 and Y for item2" or "X on item1 and Y on item2" where items differ
        val regex = Regex("""\d+(?:[.,]\d+)?\s*(?:rupees|rs|₹|\$)?\s*(?:for|on)\s+([a-z]+)\s+and\s+\d+(?:[.,]\d+)?\s*(?:rupees|rs|₹|\$)?\s*(?:for|on)\s+([a-z]+)""")
        val match = regex.find(lower)
        if (match != null) {
            val item1 = match.groupValues[1]
            val item2 = match.groupValues[2]
            if ((item1 != item2) && (item1 != "another")) {
                return true
            }
        }
        return false
    }

    private fun extractMathExpression(text: String): Pair<String?, Boolean> {
        var lower = text.lowercase()

        // Multi-word operator phrases replacement
        lower = lower
            .replace("plus another", " + ")
            .replace("and another", " + ")
            .replace("added to", " + ")
            .replace("multiplied by", " * ")
            .replace("multiply by", " * ")
            .replace("divided by", " / ")
            .replace("divide by", " / ")
            .replace("subtracted by", " - ")

        // Strip currency words to prevent breaking arithmetic expression parsing
        lower = lower
            .replace("rupees", " ")
            .replace("rupee", " ")
            .replace("rs.", " ")
            .replace("rs", " ")
            .replace("₹", " ")
            .replace("dollars", " ")
            .replace("dollar", " ")
            .replace("$", " ")
            .replace("euros", " ")
            .replace("euro", " ")
            .replace("€", " ")
            .replace("pounds", " ")
            .replace("pound", " ")
            .replace("£", " ")

        // Replace "and" between numbers: e.g. "25 and 50" -> "25 + 50"
        val andBetweenNumbers = Regex("""(\d+(?:[.,]\d+)?)\s+and\s+(\d+(?:[.,]\d+)?)""")
        lower = andBetweenNumbers.replace(lower, "$1 + $2")

        // Replace "spent X ... and another Y" -> "X + Y"
        val spentAndAnother = Regex("""(\d+(?:[.,]\d+)?)\s+[^0-9]+\s+and\s+another\s+(\d+(?:[.,]\d+)?)""")
        lower = spentAndAnother.replace(lower, "$1 + $2")

        // Replace word operators with symbols
        lower = lower
            .replace(Regex("""\bplus\b"""), " + ")
            .replace(Regex("""\badd\b"""), " + ")
            .replace(Regex("""\bminus\b"""), " - ")
            .replace(Regex("""\bsubtract\b"""), " - ")
            .replace(Regex("""\bless\b"""), " - ")
            .replace(Regex("""\btimes\b"""), " * ")
            .replace(Regex("""\binto\b"""), " * ")
            .replace(Regex("""\bover\b"""), " / ")

        // Check for invalid adjacent operator sequences (e.g. "+ *")
        if (Regex("""[+\-*/]\s*[+\-*/]""").containsMatchIn(lower)) {
            return Pair(null, true)
        }

        // Tokenize into numbers and operators
        val tokenRegex = Regex("""\d+(?:[.,]\d+)?|[+\-*/]""")
        val tokens = tokenRegex.findAll(lower).map { it.value }.toList()

        val numbers = tokens.filter { it.first().isDigit() }
        val operators = tokens.filter { !it.first().isDigit() }

        if (numbers.size >= 2 && operators.isNotEmpty()) {
            val exprBuilder = StringBuilder()
            var expectNumber = true
            var validStructure = true

            for (token in tokens) {
                val isNum = token.first().isDigit()
                if (expectNumber && isNum) {
                    exprBuilder.append(token).append(" ")
                    expectNumber = false
                } else if (!expectNumber && !isNum) {
                    exprBuilder.append(token).append(" ")
                    expectNumber = true
                } else {
                    validStructure = false
                    break
                }
            }

            if (validStructure && !expectNumber) {
                return Pair(exprBuilder.toString().trim(), false)
            } else {
                return Pair(null, true)
            }
        }

        return Pair(null, false)
    }
}
