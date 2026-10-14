package com.theveloper.pixelplay.data.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchPriceConverterTest {

    @Test
    fun testAudPrices() {
        assertEquals("$45 AUD", MerchPriceConverter.formatAudPrice("AUD 45.00"))
        assertEquals("$45 AUD", MerchPriceConverter.formatAudPrice("$45.00 AUD"))
        assertEquals("$45 AUD", MerchPriceConverter.formatAudPrice("45 AUD"))
        assertEquals("$45 AUD", MerchPriceConverter.formatAudPrice("A$45"))
        assertEquals("$45 AUD", MerchPriceConverter.formatAudPrice("A$45.00"))
    }

    @Test
    fun testUsdPrices() {
        // 35.00 * 1.55 = 54.25 -> 54
        assertEquals("$54 AUD", MerchPriceConverter.formatAudPrice("$35.00"))
        assertEquals("$54 AUD", MerchPriceConverter.formatAudPrice("USD 35.00"))
        assertEquals("$54 AUD", MerchPriceConverter.formatAudPrice("$35.00 USD"))
        assertEquals("$54 AUD", MerchPriceConverter.formatAudPrice("35 USD"))
    }

    @Test
    fun testGbpPrices() {
        // 25.00 * 1.95 = 48.75 -> 49
        assertEquals("$49 AUD", MerchPriceConverter.formatAudPrice("£25.00"))
        assertEquals("$49 AUD", MerchPriceConverter.formatAudPrice("25.00 GBP"))
        assertEquals("$49 AUD", MerchPriceConverter.formatAudPrice("GBP 25"))
    }

    @Test
    fun testEurPrices() {
        // 30.00 * 1.65 = 49.5 -> 50
        assertEquals("$50 AUD", MerchPriceConverter.formatAudPrice("€30.00"))
        assertEquals("$50 AUD", MerchPriceConverter.formatAudPrice("30.00 EUR"))
        assertEquals("$50 AUD", MerchPriceConverter.formatAudPrice("EUR 30"))
    }

    @Test
    fun testCadPrices() {
        // 50.00 * 1.12 = 56.0 -> 56
        assertEquals("$56 AUD", MerchPriceConverter.formatAudPrice("CAD 50.00"))
        assertEquals("$56 AUD", MerchPriceConverter.formatAudPrice("CA$50.00"))
    }

    @Test
    fun testEdgeCasesAndNull() {
        assertNull(MerchPriceConverter.formatAudPrice(null))
        assertNull(MerchPriceConverter.formatAudPrice(""))
        assertNull(MerchPriceConverter.formatAudPrice("   "))
        assertEquals("Free", MerchPriceConverter.formatAudPrice("Free"))
        assertEquals("Sold Out", MerchPriceConverter.formatAudPrice("Sold Out"))
    }
}
