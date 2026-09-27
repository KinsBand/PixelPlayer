package com.theveloper.pixelplay.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MixRefillPolicyTest {
    @Test fun `refill begins below three upcoming tracks`() {
        assertFalse(MixRefillPolicy.shouldRefill(5, 1))
        assertTrue(MixRefillPolicy.shouldRefill(5, 2))
        assertTrue(MixRefillPolicy.shouldRefill(5, 4))
    }
    @Test fun `invalid or not yet installed queue cannot trigger refill`() {
        assertFalse(MixRefillPolicy.shouldRefill(0, -1))
        assertFalse(MixRefillPolicy.shouldRefill(4, -1))
        assertFalse(MixRefillPolicy.shouldRefill(4, 4))
    }
}
