package com.kanak.app.voice

import com.kanak.app.model.ExpenseCategory

data class VoiceDraft(
    val amountText: String,
    val category: ExpenseCategory?,
    val note: String
)

/**
 * Utility for parsing spoken transcripts into editable draft fields (amount, category, note)
 * using deterministic local keyword classification.
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
            "breakfast", "tea", "coffee", "snack", "snacks", "bakery", "cake", "ice cream", "dessert"
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
            "bike service", "car service", "puncture", "mechanic"
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

        val amountRegex = Regex("""[$€£₹]?\s*(\d+(?:[.,]\d{1,2})?)""")
        val match = amountRegex.find(trimmed)
        val amountText = match?.groupValues?.get(1)?.replace(",", ".") ?: ""

        val category = inferCategory(trimmed)

        return VoiceDraft(
            amountText = amountText,
            category = category,
            note = trimmed
        )
    }
}
