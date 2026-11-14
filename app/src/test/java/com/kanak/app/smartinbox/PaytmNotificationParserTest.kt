package com.kanak.app.smartinbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaytmNotificationParserTest {
    @Test fun confirmedPaytmDebitIsParsedLocally() {
        val parsed = PaytmNotificationParser.parse("net.one97.paytm", "Paid ₹125.50 to Daana Paani")
        requireNotNull(parsed)
        assertEquals(12_550L, parsed.amountMinor)
        assertEquals("Daana Paani", parsed.merchant)
    }
    @Test fun creditsRefundsAmbiguousAndOtherAppsAreIgnored() {
        assertNull(PaytmNotificationParser.parse("net.one97.paytm", "₹50 credited to your account"))
        assertNull(PaytmNotificationParser.parse("net.one97.paytm", "Refund of ₹50 received"))
        assertNull(PaytmNotificationParser.parse("net.one97.paytm", "Transaction update ₹50"))
        assertNull(PaytmNotificationParser.parse("com.example.bank", "Paid ₹50 to Cafe"))
    }
}
