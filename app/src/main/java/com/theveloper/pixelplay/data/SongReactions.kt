package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quick reactions from the lyrics screen. They are **not** likes: ❤️ never touches Liked Songs,
 * and 👎 never excludes a song on its own. Each one is a separate signal with its own axis so the
 * recommendation / mixing side can treat "strong song preference", "fits this session's vibe"
 * and "not engaging" differently.
 */
enum class SongReaction(
    val emoji: String,
    val label: String,
    val axis: Axis,
    /** +1 positive, -1 negative. */
    val polarity: Int,
    /** 0..1: how strong the signal is on its axis. */
    val strength: Double,
) {
    LOVE("❤️", "Love", Axis.SONG_PREFERENCE, +1, 1.0),
    FIRE("🔥", "Fire", Axis.SESSION_VIBE, +1, 1.0),
    ALRIGHT("👌", "Alright", Axis.SONG_PREFERENCE, +1, 0.4),
    NOT_THE_VIBE("😐", "Not the vibe", Axis.SESSION_VIBE, -1, 0.7),
    BORING("🥱", "Boring", Axis.ENGAGEMENT, -1, 0.6),
    DISLIKE("👎", "Don't like", Axis.SONG_PREFERENCE, -1, 1.0);

    enum class Axis {
        /** Lasting taste for this recording. */
        SONG_PREFERENCE,
        /** Fit with the current listening session's energy / vibe; should fade after the session. */
        SESSION_VIBE,
        /** How engaging the song felt right now (energy / attention). */
        ENGAGEMENT,
    }

    val isPositive: Boolean get() = polarity > 0

    /** Signed weight, e.g. for a future scorer: polarity × strength. */
    val signedWeight: Double get() = polarity * strength

    companion object {
        val positive: List<SongReaction> = listOf(LOVE, FIRE, ALRIGHT)
        val negative: List<SongReaction> = listOf(NOT_THE_VIBE, BORING, DISLIKE)
        fun fromName(name: String?): SongReaction? = entries.firstOrNull { it.name == name }
    }
}

@Singleton
class SongReactions @Inject constructor(
    private val learning: MixLearning,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Latest reaction per song id in this process, for instant UI feedback. */
    private val _latest = MutableStateFlow<Map<String, SongReaction>>(emptyMap())
    val latest: StateFlow<Map<String, SongReaction>> = _latest.asStateFlow()

    /**
     * Stores one reaction. Every event is kept (not only the latest) so the history shows
     * changes of heart; readers that want one answer per song use [latestFor].
     */
    fun record(song: Song, reaction: SongReaction, positionMs: Long, recordingId: String? = null) {
        _latest.update { it + (song.id to reaction) }
        val entry = SongReactionEntry(
            id = UUID.randomUUID().toString(),
            songId = song.id,
            recordingId = recordingId,
            sessionId = learning.sessionId,
            mixId = learning.mixId,
            reaction = reaction.name,
            axis = reaction.axis.name,
            polarity = reaction.polarity,
            strength = reaction.strength,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = song.duration,
            title = song.title,
            artist = song.displayArtist,
            createdAt = System.currentTimeMillis(),
        )
        scope.launch {
            try {
                val dao = learning.reactionDao()
                dao.save(entry)
                dao.compact()
            } catch (error: Exception) {
                Timber.w(error, "Reaction write failed")
            }
        }
    }

    /** Reactions given during the current listening session, newest first. */
    suspend fun forCurrentSession(): List<SongReactionEntry> = withContext(Dispatchers.IO) {
        learning.reactionDao().forSession(learning.sessionId)
    }

    suspend fun latestFor(songId: String): SongReaction? = withContext(Dispatchers.IO) {
        _latest.value[songId] ?: SongReaction.fromName(learning.reactionDao().latestFor(songId)?.reaction)
    }

    suspend fun recent(): List<SongReactionEntry> = withContext(Dispatchers.IO) {
        learning.reactionDao().recent()
    }
}
