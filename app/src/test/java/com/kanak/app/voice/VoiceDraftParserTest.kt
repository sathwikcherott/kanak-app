package com.kanak.app.voice

import com.kanak.app.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceDraftParserTest {

    @Test
    fun parseTranscript_extractsAmountAndCategoryForFood() {
        val draft = VoiceDraftParser.parseTranscript("25.50 dollars for lunch")
        assertEquals("25.50", draft.amountText)
        assertEquals(ExpenseCategory.FOOD, draft.category)
        assertEquals("25.50 dollars for lunch", draft.note)
    }

    @Test
    fun parseTranscript_extractsAmountAndCategoryForTransport() {
        val draft = VoiceDraftParser.parseTranscript("15.00 uber taxi cab")
        assertEquals("15.00", draft.amountText)
        assertEquals(ExpenseCategory.TRANSPORT, draft.category)
    }

    @Test
    fun parseTranscript_extractsAmountAndCategoryForShopping() {
        val draft = VoiceDraftParser.parseTranscript("$100 target shopping")
        assertEquals("100", draft.amountText)
        assertEquals(ExpenseCategory.SHOPPING, draft.category)
    }

    @Test
    fun parseTranscript_handlesNoCategoryKeywordGracefully() {
        val draft = VoiceDraftParser.parseTranscript("42.00 random expense")
        assertEquals("42.00", draft.amountText)
        assertNull(draft.category)
        assertEquals("42.00 random expense", draft.note)
    }

    @Test
    fun parseTranscript_handlesEmptyOrBlankInput() {
        val draft = VoiceDraftParser.parseTranscript("")
        assertEquals("", draft.amountText)
        assertNull(draft.category)
        assertEquals("", draft.note)
    }
}
