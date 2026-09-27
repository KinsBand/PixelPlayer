package com.theveloper.pixelplay.data.service.player

/**
 * Where a crossfade should sit relative to the *music*, not the file.
 *
 * Built from the analysis rows of the two tracks (trailing silence of the
 * outgoing one, leading silence of the incoming one). Pure Kotlin so the policy
 * can be unit-tested without a player.
 *
 * @param incomingStartMs position the incoming track is prepared at, so its
 *        leading silence is not played inside the fade.
 * @param outgoingSilenceEndMs measured trailing silence of the outgoing track;
 *        the fade is scheduled to end where that silence begins.
 */
internal data class TransitionTrim(
    val incomingStartMs: Long = 0L,
    val outgoingSilenceEndMs: Long = 0L,
) {
    /**
     * How much of the outgoing track's end to skip, given its real duration.
     * Returns 0 when the measurement is implausible for this duration (e.g. a
     * stale analysis row for a file that has since been replaced).
     */
    fun tailTrimFor(durationMs: Long): Long {
        if (durationMs <= 0L || outgoingSilenceEndMs < TransitionTrimPolicy.MIN_TRIM_MS) return 0L
        if (outgoingSilenceEndMs > TransitionTrimPolicy.MAX_TAIL_TRIM_MS) return 0L
        if (outgoingSilenceEndMs * 3 > durationMs) return 0L
        return outgoingSilenceEndMs
    }

    companion object {
        val NONE = TransitionTrim()
    }
}

internal object TransitionTrimPolicy {
    /** Silences shorter than this are left alone (normal track spacing). */
    const val MIN_TRIM_MS = 150L

    /**
     * Longest leading silence that is skipped. Anything longer is more likely a
     * deliberate quiet intro (or a detection problem) than dead air.
     */
    const val MAX_LEADING_SKIP_MS = 15_000L

    /** Longest trailing silence that is skipped (hidden-track gaps are longer). */
    const val MAX_TAIL_TRIM_MS = 30_000L

    /** Start slightly before the first sound so its attack is not clipped. */
    const val ONSET_PREROLL_MS = 30L

    fun resolve(
        outgoingSilenceEndMs: Long?,
        incomingSilenceStartMs: Long?,
    ): TransitionTrim {
        val lead = incomingSilenceStartMs ?: 0L
        val incomingStart = if (lead in MIN_TRIM_MS..MAX_LEADING_SKIP_MS) {
            (lead - ONSET_PREROLL_MS).coerceAtLeast(0L)
        } else {
            0L
        }
        return TransitionTrim(
            incomingStartMs = incomingStart,
            outgoingSilenceEndMs = (outgoingSilenceEndMs ?: 0L).coerceAtLeast(0L),
        )
    }
}

/**
 * Descriptor of a queue item used to decide whether two neighbours are
 * consecutive tracks of the same album.
 */
internal data class AlbumPosition(
    val albumTitle: String?,
    val trackNumber: Int,
    val directory: String?,
)

/**
 * True when [next] is the track directly after [current] on the same album
 * (same album title, track n → n+1, and — when both are files — the same folder).
 *
 * Albums are mastered as a sequence: the gaps, segues and run-ins between their
 * tracks are part of the record, so a crossfade here damages it. Track numbers
 * of the form disc×1000+track still compare correctly within a disc.
 */
internal fun isConsecutiveAlbumPair(current: AlbumPosition, next: AlbumPosition): Boolean {
    val albumA = current.albumTitle?.trim().orEmpty()
    val albumB = next.albumTitle?.trim().orEmpty()
    if (albumA.isEmpty() || !albumA.equals(albumB, ignoreCase = true)) return false
    if (current.trackNumber <= 0 || next.trackNumber != current.trackNumber + 1) return false
    val dirA = current.directory?.takeIf { it.isNotBlank() }
    val dirB = next.directory?.takeIf { it.isNotBlank() }
    if (dirA != null && dirB != null && dirA != dirB) return false
    return true
}
