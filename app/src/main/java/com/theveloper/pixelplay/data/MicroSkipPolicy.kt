package com.theveloper.pixelplay.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "micro_skip_cooldowns")
data class MicroSkipCooldown(
    @PrimaryKey val vector: String,
    val sessionId: String,
    val expiresAt: Long,
)

/** Explicit skips only. Seeks, resumed excerpts and interrupted plays are not rejection signals. */
internal object MicroSkipPolicy {
    const val WINDOW_MS = 15_000L
    const val COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000

    fun qualifies(attempt: MixAttempt): Boolean = attempt.endReason == MixEndReason.SKIP.name &&
        attempt.activeMs in 0 until WINDOW_MS && attempt.startPositionMs in 0..250L &&
        attempt.endPositionMs in 0 until WINDOW_MS && attempt.seeks == 0

    fun vectors(artist: String?, genre: String?): Set<String> = buildSet {
        artist?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it != "<unknown>" && it != "unknown artist" }
            ?.let { add("artist:$it") }
        // Only use a specific subgenre, never infer one from a broad family such as Rock.
        GenreTaxonomy.match(genre)?.takeIf { it.id != it.family }?.let { add("genre:${it.id}") }
    }

    fun triggered(previous: MixAttempt?, current: MixAttempt): List<MicroSkipCooldown> {
        if (previous == null || previous.id == current.id || current.sessionId == null ||
            previous.sessionId != current.sessionId || !qualifies(previous) || !qualifies(current)) return emptyList()
        return (vectors(previous.artist, previous.genre) intersect vectors(current.artist, current.genre)).map {
            MicroSkipCooldown(it, current.sessionId, current.startedAt + current.activeMs + COOLDOWN_MS)
        }
    }

    fun penalty(artist: String?, genre: String?, cooldowns: List<MicroSkipCooldown>, sessionId: String?, now: Long): Double {
        val keys = vectors(artist, genre)
        return cooldowns.filter { it.vector in keys && it.expiresAt > now }
            .maxOfOrNull { if (it.sessionId == sessionId) 8.0 else 3.0 } ?: 0.0
    }
}
