package com.kanak.app.voice

import com.kanak.app.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDraftParserTest {

    @Test
    fun parseTranscript_preservesOperatorsAndDescription() {
        val cases = mapOf(
            "25+50 bus" to "75",
            "25×4 coffee" to "100",
            "25x4 coffee" to "100",
            "100 / (2 + 3) × 3 food" to "60",
            "1,000 plus 25.50 bus" to "1025.5",
            "100 ÷ 3 food" to "33.33",
            "25 plus 50 for bus route 500" to "75"
        )
        for ((transcript, expected) in cases) {
            val draft = VoiceDraftParser.parseTranscript(transcript)
            assertEquals(transcript, expected, draft.amountText)
            assertEquals(transcript, draft.note)
        }
    }

    @Test
    fun parseTranscript_requestsReviewWhenOperatorOrOperandIsMissing() {
        for (transcript in listOf("25 50 bus", "25 plus bus", "500 divided by zero dinner", "25 x coffee")) {
            val draft = VoiceDraftParser.parseTranscript(transcript)
            assertTrue(transcript, draft.hasUncertainArithmetic)
            assertEquals("", draft.amountText)
            assertEquals(transcript, draft.note)
        }
    }

    @Test
    fun parseTranscript_voiceSpokenArithmeticExamples() {
        // 1. "25 plus 50 bus" -> ₹75, Transport
        val d1 = VoiceDraftParser.parseTranscript("25 plus 50 bus")
        assertEquals("75", d1.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, d1.category)
        assertFalse(d1.isAmbiguous)
        assertFalse(d1.hasUncertainArithmetic)

        // 2. "25 and 50 for BMTC" -> ₹75, Transport
        val d2 = VoiceDraftParser.parseTranscript("25 and 50 for BMTC")
        assertEquals("75", d2.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, d2.category)

        // 3. "100 plus 50 plus 20 food" -> ₹170, Food
        val d3 = VoiceDraftParser.parseTranscript("100 plus 50 plus 20 food")
        assertEquals("170", d3.amountText)
        assertEquals(ExpenseCategory.FOOD, d3.category)

        // 4. "200 minus 50 shopping" -> ₹150, Shopping
        val d4 = VoiceDraftParser.parseTranscript("200 minus 50 shopping")
        assertEquals("150", d4.amountText)
        assertEquals(ExpenseCategory.SHOPPING, d4.category)

        // 5. "25 times 4 coffee" -> ₹100, Food
        val d5 = VoiceDraftParser.parseTranscript("25 times 4 coffee")
        assertEquals("100", d5.amountText)
        assertEquals(ExpenseCategory.FOOD, d5.category)

        // 6. "500 divided by 2 dinner" -> ₹250, Food
        val d6 = VoiceDraftParser.parseTranscript("500 divided by 2 dinner")
        assertEquals("250", d6.amountText)
        assertEquals(ExpenseCategory.FOOD, d6.category)

        // 7. "30 rupees plus 40 rupees auto" -> ₹70, Transport
        val d7 = VoiceDraftParser.parseTranscript("30 rupees plus 40 rupees auto")
        assertEquals("70", d7.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, d7.category)

        // 8. "Spent 50 and another 25 on bus" -> ₹75, Transport
        val d8 = VoiceDraftParser.parseTranscript("Spent 50 and another 25 on bus")
        assertEquals("75", d8.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, d8.category)

        // Natural speech: "I spent 25 on bus and another 50 on bus"
        val d9 = VoiceDraftParser.parseTranscript("I spent 25 on bus and another 50 on bus")
        assertEquals("75", d9.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, d9.category)
    }

    @Test
    fun parseTranscript_decimalAmountsAndOperatorPrecedence() {
        val d1 = VoiceDraftParser.parseTranscript("25.50 plus 50.25 bus")
        assertEquals("75.75", d1.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, d1.category)

        val d2 = VoiceDraftParser.parseTranscript("10 plus 20 times 3 food")
        assertEquals("70", d2.amountText)
        assertEquals(ExpenseCategory.FOOD, d2.category)
    }

    @Test
    fun parseTranscript_ambiguousMultiTransaction_returnsAmbiguousFlag() {
        val d = VoiceDraftParser.parseTranscript("25 for lunch and 50 for dinner")
        assertTrue(d.isAmbiguous)
        assertEquals("", d.amountText)
    }

    @Test
    fun parseTranscript_invalidOrUncertainArithmetic_returnsUncertainFlag() {
        val d1 = VoiceDraftParser.parseTranscript("25 plus times 50")
        assertTrue(d1.hasUncertainArithmetic)
        assertEquals("", d1.amountText)

        val d2 = VoiceDraftParser.parseTranscript("500 divided by 0 dinner")
        assertTrue(d2.hasUncertainArithmetic)
        assertEquals("", d2.amountText)
    }

    @Test
    fun parseTranscript_swathiKeywordExamples_infersGirlfriendCategoryAndPreservesDescription() {
        // 1. "120 rupees for Swathi"
        val draft1 = VoiceDraftParser.parseTranscript("120 rupees for Swathi")
        assertEquals("120", draft1.amountText)
        assertEquals(ExpenseCategory.GIRLFRIEND, draft1.category)
        assertEquals("120 rupees for Swathi", draft1.note)

        // 2. "Bought flowers for Swathi"
        val draft2 = VoiceDraftParser.parseTranscript("500 bought flowers for Swathi")
        assertEquals("500", draft2.amountText)
        assertEquals(ExpenseCategory.GIRLFRIEND, draft2.category)
        assertEquals("500 bought flowers for Swathi", draft2.note)

        // 3. "Dinner with Swathi"
        val draft3 = VoiceDraftParser.parseTranscript("1500 dinner with Swathi")
        assertEquals("1500", draft3.amountText)
        assertEquals(ExpenseCategory.GIRLFRIEND, draft3.category)
        assertEquals("1500 dinner with Swathi", draft3.note)

        // 4. "Gift for Swathi"
        val draft4 = VoiceDraftParser.parseTranscript("2000 gift for Swathi")
        assertEquals("2000", draft4.amountText)
        assertEquals(ExpenseCategory.GIRLFRIEND, draft4.category)
        assertEquals("2000 gift for Swathi", draft4.note)

        // 5. "Swathi's birthday gift"
        val draft5 = VoiceDraftParser.parseTranscript("3000 Swathi's birthday gift")
        assertEquals("3000", draft5.amountText)
        assertEquals(ExpenseCategory.GIRLFRIEND, draft5.category)
        assertEquals("3000 Swathi's birthday gift", draft5.note)
    }

    @Test
    fun parseTranscript_chickenBiryani_infersFoodAndDining() {
        val draft = VoiceDraftParser.parseTranscript("120 chicken biryani")
        assertEquals("120", draft.amountText)
        assertEquals(ExpenseCategory.FOOD, draft.category)
        assertEquals("120 chicken biryani", draft.note)
    }

    @Test
    fun parseTranscript_riceAndVegetables_infersGroceries() {
        val draft = VoiceDraftParser.parseTranscript("250 rice and vegetables")
        assertEquals("250", draft.amountText)
        assertEquals(ExpenseCategory.GROCERIES, draft.category)
    }

    @Test
    fun parseTranscript_busTicket_infersTransport() {
        val draft = VoiceDraftParser.parseTranscript("80 bus ticket")
        assertEquals("80", draft.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, draft.category)
    }

    @Test
    fun parseTranscript_electricityBill_infersBillsAndUtilities() {
        val draft = VoiceDraftParser.parseTranscript("500 electricity bill")
        assertEquals("500", draft.amountText)
        assertEquals(ExpenseCategory.BILL, draft.category)
    }

    @Test
    fun parseTranscript_unknownDescription_returnsUncategorizedNullCategory() {
        val draft = VoiceDraftParser.parseTranscript("350 random expense item")
        assertEquals("350", draft.amountText)
        assertNull(draft.category)
    }

    @Test
    fun parseTranscript_restaurantBill_prioritizesFoodOverBill() {
        val draft = VoiceDraftParser.parseTranscript("750 restaurant bill")
        assertEquals("750", draft.amountText)
        assertEquals(ExpenseCategory.FOOD, draft.category)
    }

    @Test
    fun parseTranscript_handlesIncompleteOrMalformedSpeechTranscripts() {
        val draft1 = VoiceDraftParser.parseTranscript("coffee at cafe")
        assertEquals("", draft1.amountText)
        assertEquals(ExpenseCategory.FOOD, draft1.category)

        val draft2 = VoiceDraftParser.parseTranscript("₹500")
        assertEquals("500", draft2.amountText)
        assertNull(draft2.category)

        val draft3 = VoiceDraftParser.parseTranscript("   ")
        assertEquals("", draft3.amountText)
        assertNull(draft3.category)
    }
}
