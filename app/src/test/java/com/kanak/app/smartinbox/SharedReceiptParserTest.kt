package com.kanak.app.smartinbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedReceiptParserTest {
    @Test fun confirmedSharedPaymentProducesOnlyReviewFields() {
        val receipt = SharedReceiptParser.parse("Payment successful. Paid ₹249.00 to Corner Cafe.")
        requireNotNull(receipt)
        assertEquals(24_900L, receipt.amountMinor)
        assertEquals("Corner Cafe", receipt.merchant)
    }

    @Test fun creditsRefundsAndAmbiguousTextAreNotImported() {
        assertNull(SharedReceiptParser.parse("₹249 credited to your account"))
        assertNull(SharedReceiptParser.parse("Refund of ₹249 received"))
        assertNull(SharedReceiptParser.parse("Transaction ₹249"))
    }
}
