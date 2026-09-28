package com.theveloper.pixelplay.data.service.player

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SmartResumePolicyTest {
    private fun resume(after: Long, position: Long = 60_000, phrase: Long? = null): ResumeAction? {
        val policy = SmartResumePolicy()
        policy.paused("entry", position, 1_000)
        return policy.resume("entry", position, 1_000 + after, phrase)
    }

    @Test fun `short interruption preserves offset`() {
        assertEquals(ResumeAction(60_000, 0), resume(29_999))
    }
    @Test fun `threshold and five minute boundary rewind seven seconds`() {
        assertEquals(ResumeAction(53_000, 300), resume(30_000))
        assertEquals(ResumeAction(53_000, 300), resume(300_000, phrase = 40_000))
    }
    @Test fun `long interruption uses a valid phrase or fallback`() {
        assertEquals(ResumeAction(40_000, 300), resume(300_001, phrase = 40_000))
        assertEquals(ResumeAction(53_000, 300), resume(300_001))
        assertEquals(ResumeAction(53_000, 300), resume(300_001, phrase = 90_000))
    }
    @Test fun `rewind clamps at track start`() {
        assertEquals(ResumeAction(0, 300), resume(30_000, position = 2_000))
    }
    @Test fun `changed occurrence and explicit paused seek are respected`() {
        val policy = SmartResumePolicy()
        policy.paused("first copy", 60_000, 0)
        assertNull(policy.resume("second copy", 60_000, 60_000))
        policy.paused("entry", 60_000, 0)
        assertNull(policy.resume("entry", 90_000, 60_000))
    }
    @Test fun `pause is consumed once and repeated notifications preserve original timestamp`() {
        val policy = SmartResumePolicy()
        policy.paused("entry", 60_000, 0)
        policy.paused("entry", 60_000, 59_000)
        assertEquals(ResumeAction(53_000, 300), policy.resume("entry", 60_000, 60_000))
        assertNull(policy.resume("entry", 60_000, 90_000))
    }
}
