package com.kanak.app.voice

import com.kanak.app.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceDraftParserTest {

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
