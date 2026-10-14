package com.theveloper.pixelplay.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** "After which song" placement for a single song (Soon slider). Queue [A▶, B, C, D]. */
class SongAfterUpcomingIndexTest {
    @Test fun `zero lands right after the playing song`() {
        assertEquals(1, songAfterUpcomingIndex(currentIndex = 0, itemCount = 4, upcoming = 0))
    }

    @Test fun `after the second upcoming song`() {
        // [A, B, C, X, D]
        assertEquals(3, songAfterUpcomingIndex(currentIndex = 0, itemCount = 4, upcoming = 2))
    }

    @Test fun `after the last upcoming song is the end`() {
        assertEquals(4, songAfterUpcomingIndex(currentIndex = 0, itemCount = 4, upcoming = 3))
    }

    @Test fun `too far is clamped to the end`() {
        assertEquals(4, songAfterUpcomingIndex(currentIndex = 0, itemCount = 4, upcoming = 99))
    }

    @Test fun `negative is treated as zero`() {
        assertEquals(1, songAfterUpcomingIndex(currentIndex = 0, itemCount = 4, upcoming = -2))
    }

    @Test fun `nothing playing appends`() {
        assertEquals(4, songAfterUpcomingIndex(currentIndex = -1, itemCount = 4, upcoming = 1))
    }

    @Test fun `moving a queued copy counts without it`() {
        // C (index 2) already queued; the reduced queue [A, B, D] has 3 items. After D = index 3.
        assertEquals(3, songAfterUpcomingIndex(currentIndex = 0, itemCount = 3, upcoming = 2))
        // After the playing song: index 1.
        assertEquals(1, songAfterUpcomingIndex(currentIndex = 0, itemCount = 3, upcoming = 0))
    }

    @Test fun `current song in the middle of the queue`() {
        // [A, B▶, C, D, E]: after C = index 3.
        assertEquals(3, songAfterUpcomingIndex(currentIndex = 1, itemCount = 5, upcoming = 1))
    }
}
