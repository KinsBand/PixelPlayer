package com.theveloper.pixelplay.data.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Guards the Up next notification's list: play order, never wraps to the playing song, stops at the end. */
class UpNextIndicesTest {
    private fun linear(size: Int): (Int) -> Int = { i -> if (i + 1 < size) i + 1 else -1 }

    @Test fun `next three in order`() = assertEquals(listOf(2, 3, 4), upcomingIndices(1, next = linear(10)))

    @Test fun `stops at the end of the queue`() = assertEquals(listOf(4), upcomingIndices(3, next = linear(5)))

    @Test fun `last song has nothing next`() = assertEquals(emptyList<Int>(), upcomingIndices(4, next = linear(5)))

    @Test fun `follows a shuffled order`() {
        val order = mapOf(0 to 3, 3 to 1, 1 to 2, 2 to -1)
        assertEquals(listOf(3, 1, 2), upcomingIndices(0) { order.getValue(it) })
    }

    @Test fun `a loop back to the playing song stops the list`() {
        // Two songs with repeat-all style wrapping must not list the playing song again.
        assertEquals(listOf(1), upcomingIndices(0) { if (it == 0) 1 else 0 })
    }

    @Test fun `no current song`() = assertEquals(emptyList<Int>(), upcomingIndices(-1, next = linear(3)))
}
