package com.theveloper.pixelplay.presentation.viewmodel

/** What a tapped song card shows while its playback starts. */
enum class SongStartPhase { STARTING, FAILED }

/**
 * The song the user just asked to play, from the tap until audio starts (then cleared), or
 * until it fails. [token] ties later updates to this request: a newer tap gets a newer token,
 * so a late failure or timeout from an older request never touches it.
 */
data class SongStartRequest(
    val songId: String,
    val phase: SongStartPhase,
    val token: Long,
    /**
     * The id it actually plays under when that differs from the card's (a Spotify / catalog
     * song matched to its YouTube audio gets a new id). Starting under either id counts.
     */
    val playbackId: String? = null,
) {
    fun matches(mediaId: String?): Boolean = mediaId != null && (mediaId == songId || mediaId == playbackId)
}

/** Pure transitions for [SongStartRequest] (unit-tested in SongStartRequestTest). */
object SongStartReducer {
    /** A new tap: always replaces whatever was there. */
    fun start(songId: String, token: Long): SongStartRequest =
        SongStartRequest(songId, SongStartPhase.STARTING, token)

    /** Fails [current] only if it's still the same, still-starting request. */
    fun fail(current: SongStartRequest?, token: Long): SongStartRequest? =
        if (current != null && current.token == token && current.phase == SongStartPhase.STARTING) {
            current.copy(phase = SongStartPhase.FAILED)
        } else {
            current
        }

    /** Any error while something is starting fails the request that's in flight. */
    fun failInFlight(current: SongStartRequest?): SongStartRequest? =
        if (current?.phase == SongStartPhase.STARTING) current.copy(phase = SongStartPhase.FAILED) else current

    /**
     * Audio started on [mediaId]: that song's request is done. This also clears a failed state
     * when the player recovered by itself (e.g. a stream retry after an error).
     */
    fun started(current: SongStartRequest?, mediaId: String?): SongStartRequest? =
        if (current != null && current.matches(mediaId)) {
            null
        } else {
            current
        }

    /** The failed state's time is up: clear it unless a newer request replaced it. */
    fun expire(current: SongStartRequest?, token: Long): SongStartRequest? =
        if (current != null && current.token == token && current.phase == SongStartPhase.FAILED) null else current

    /** The tapped song [songId] will play as [playbackId] (after matching). */
    fun withPlaybackId(current: SongStartRequest?, songId: String, playbackId: String): SongStartRequest? =
        if (current != null && current.songId == songId && playbackId != songId) current.copy(playbackId = playbackId) else current

    /** The phase a card for [songId] should show. */
    fun phaseFor(current: SongStartRequest?, songId: String): SongStartPhase? =
        current?.takeIf { it.songId == songId }?.phase
}
