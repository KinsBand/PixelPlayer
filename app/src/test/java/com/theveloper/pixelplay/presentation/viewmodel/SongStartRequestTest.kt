package com.theveloper.pixelplay.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SongStartRequestTest {

    @Test
    fun `a late failure from an older tap doesn't fail the newer request`() {
        val newer = SongStartReducer.start("b", token = 2)
        assertEquals(newer, SongStartReducer.fail(newer, token = 1))
    }

    @Test
    fun `failing the current request shows FAILED`() {
        val req = SongStartReducer.start("a", token = 5)
        assertEquals(SongStartPhase.FAILED, SongStartReducer.fail(req, token = 5)?.phase)
    }

    @Test
    fun `audio starting on another song leaves the request alone`() {
        val req = SongStartReducer.start("a", token = 1)
        assertEquals(req, SongStartReducer.started(req, "other"))
        assertEquals(req, SongStartReducer.started(req, null))
    }

    @Test
    fun `audio starting on the tapped song clears it`() {
        assertNull(SongStartReducer.started(SongStartReducer.start("a", 1), "a"))
    }

    @Test
    fun `a failed card clears when the player recovers and that song starts`() {
        val failed = SongStartReducer.fail(SongStartReducer.start("a", 1), 1)
        assertNull(SongStartReducer.started(failed, "a"))
        assertEquals(failed, SongStartReducer.started(failed, "b"))
    }

    @Test
    fun `expiry only clears the same failed request`() {
        val failed = SongStartReducer.fail(SongStartReducer.start("a", 3), 3)
        assertNull(SongStartReducer.expire(failed, 3))
        val newer = SongStartReducer.start("b", 4)
        assertEquals(newer, SongStartReducer.expire(newer, 3))
    }

    @Test
    fun `failInFlight ignores an already failed or empty state`() {
        assertNull(SongStartReducer.failInFlight(null))
        val failed = SongStartReducer.fail(SongStartReducer.start("a", 1), 1)
        assertEquals(failed, SongStartReducer.failInFlight(failed))
    }

    @Test
    fun `phaseFor only matches the tapped song`() {
        val req = SongStartReducer.start("a", 1)
        assertEquals(SongStartPhase.STARTING, SongStartReducer.phaseFor(req, "a"))
        assertNull(SongStartReducer.phaseFor(req, "b"))
    }
}
