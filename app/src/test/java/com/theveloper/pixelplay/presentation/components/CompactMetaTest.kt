package com.theveloper.pixelplay.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Guards the compact metadata strings: no words, the middle-dot separator, and no fake dates. */
class CompactMetaTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L

    @Test fun `relative time has no words`() {
        assertEquals("30m", CompactMeta.relativeTime(now - 30 * min, now))
        assertEquals("5h", CompactMeta.relativeTime(now - 5 * 60 * min, now))
        assertEquals("3d", CompactMeta.relativeTime(now - 3 * 24 * 60 * min, now))
    }

    @Test fun `just now and future times read as 1m, never 0m or negative`() {
        assertEquals("1m", CompactMeta.relativeTime(now - 10_000, now))
        assertEquals("1m", CompactMeta.relativeTime(now + 5 * min, now))
    }

    @Test fun `join uses the middle dot and drops blanks`() {
        assertEquals("42 · 2h 14m", CompactMeta.join("42", "2h 14m"))
        assertEquals("42", CompactMeta.join("42", null))
        assertEquals("42", CompactMeta.join("42", " "))
    }

    @Test fun `date label is dd-MM-yy and missing dates are omitted`() {
        val epoch = LocalDate.of(2026, 10, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("10/10/26", CompactMeta.dateLabel(epoch, ZoneOffset.UTC))
        assertNull(CompactMeta.dateLabel(0L, ZoneOffset.UTC))
        assertNull(CompactMeta.dateLabel(-5L, ZoneOffset.UTC))
    }

    @Test fun `album date added in seconds is scaled to milliseconds`() {
        assertEquals(1_700_000_000_000L, CompactMeta.epochMsFromMaybeSeconds(1_700_000_000L))
        assertEquals(1_700_000_000_000L, CompactMeta.epochMsFromMaybeSeconds(1_700_000_000_000L))
        assertEquals(0L, CompactMeta.epochMsFromMaybeSeconds(0L))
    }

    @Test fun `unknown year is omitted`() {
        assertNull(CompactMeta.yearLabel(0))
        assertEquals("2026", CompactMeta.yearLabel(2026))
    }

    @Test fun `duration reads 1h 59m with no words`() {
        assertEquals("1h 59m", CompactMeta.duration(119 * min))
        assertEquals("2h", CompactMeta.duration(120 * min))
        assertEquals("45m", CompactMeta.duration(45 * min + 30_000))
        assertEquals("0m", CompactMeta.duration(-5))
    }
}
