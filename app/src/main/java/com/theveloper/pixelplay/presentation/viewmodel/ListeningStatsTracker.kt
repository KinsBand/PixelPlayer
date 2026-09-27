package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.PlaybackExposure
import com.theveloper.pixelplay.data.MixEndReason
import com.theveloper.pixelplay.data.MixLearning
import com.theveloper.pixelplay.data.MixAttempt
import android.os.SystemClock
import androidx.media3.common.C
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Tracks listening statistics for songs.
 * Extracted from PlayerViewModel to reduce its size and improve modularity.
 *
 * Responsibilities:
 * - Track active listening sessions
 * - Record play statistics when session ends
 * - Handle voluntary vs automatic plays
 */
@Singleton
class ListeningStatsTracker @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val mixLearning: MixLearning,
    private val scrobbler: com.theveloper.pixelplay.data.scrobble.Scrobbler
) {
    private var playbackSpeed = 1f
    private data class RecommendationContext(val songId: String, val decisionId: String?, val sessionId: String?, val recordingId: String?)
    private var recommendationContext: RecommendationContext? = null
    @Synchronized fun setRecommendationContext(songId: String, decisionId: String?, sessionId: String?, recordingId: String?) {
        recommendationContext = RecommendationContext(songId, decisionId, sessionId, recordingId)
    }
    private var currentSession: ActiveSession? = null
    private var pendingVoluntarySongId: String? = null
    private var scope: CoroutineScope? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playbackHistory = MutableStateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>>(emptyList())
    val playbackHistory: StateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>> = _playbackHistory.asStateFlow()

    /**
     * Must be called to set the coroutine scope for async operations.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        val activeScope = scope
        if (activeScope == null || activeScope.coroutineContext[Job]?.isActive != true) {
            scope = coroutineScope
        }
        coroutineScope.launch(Dispatchers.IO) {
            _playbackHistory.value = playbackStatsRepository.loadPlaybackHistory(
                limit = MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS
            )
        }
    }

    @Synchronized
    fun onVoluntarySelection(songId: String) {
        pendingVoluntarySongId = songId
    }

    fun onSongChanged(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean
    ) {
        finalizeCurrentSession()
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val normalizedDuration = normalizeDuration(durationMs, fallbackDurationMs)

        currentSession = ActiveSession(
            songId = safeSongId,
            totalDurationMs = normalizedDuration,
            startedAtEpochMs = nowEpoch,
            lastKnownPositionMs = positionMs.coerceAtLeast(0L),
            accumulatedListeningMs = 0L,
            lastRealtimeMs = nowRealtime,
            lastUpdateEpochMs = nowEpoch,
            isPlaying = isPlaying,
            mixId = mixLearning.mixId,
            decisionId = recommendationContext?.takeIf { it.songId == safeSongId }?.decisionId,
            sessionId = recommendationContext?.takeIf { it.songId == safeSongId }?.sessionId ?: mixLearning.sessionId,
            recordingId = recommendationContext?.takeIf { it.songId == safeSongId }?.recordingId,
            exposure = PlaybackExposure(positionMs, nowRealtime, isPlaying),
            isVoluntary = pendingVoluntarySongId == safeSongId
        )
        currentSession?.let { persistAttempt(it, checkpoint = true) }
        if (pendingVoluntarySongId == safeSongId) {
            pendingVoluntarySongId = null
        }
    }

    @Synchronized
    fun onPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        sampleExposure(session, positionMs, nowRealtime, isPlaying)
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    @Synchronized
    fun onProgress(positionMs: Long, isPlaying: Boolean) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        sampleExposure(session, positionMs, nowRealtime, isPlaying)
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    fun ensureSession(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean
    ) {
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            finalizeCurrentSession()
            return
        }
        val existing = currentSession
        if (existing?.songId == safeSongId) {
            updateDuration(normalizeDuration(durationMs, fallbackDurationMs))
            val nowRealtime = SystemClock.elapsedRealtime()
            sampleExposure(existing, positionMs, nowRealtime, isPlaying)
            accumulateRealtimeListening(existing, nowRealtime)
            existing.isPlaying = isPlaying
            existing.lastRealtimeMs = nowRealtime
            existing.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            existing.lastUpdateEpochMs = System.currentTimeMillis()
            return
        }
        onTrackChanged(
            songId = safeSongId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = fallbackDurationMs,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun updateDuration(durationMs: Long) {
        val session = currentSession ?: return
        if (durationMs > 0 && durationMs != C.TIME_UNSET) {
            session.totalDurationMs = durationMs
        }
    }

    @Synchronized
    fun finalizeCurrentSession(forceSynchronousPersistence: Boolean = false) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        accumulateRealtimeListening(session, nowRealtime)
        persistAttempt(session)
        val listened = session.exposure.activeMs
        if (listened >= MIN_SESSION_LISTEN_MS) {
            val rawEndTimestamp = when {
                session.isPlaying -> nowEpoch
                session.lastUpdateEpochMs > 0L -> session.lastUpdateEpochMs
                else -> session.startedAtEpochMs + listened
            }
            val timestamp = rawEndTimestamp
                .coerceAtLeast(session.startedAtEpochMs.coerceAtLeast(0L))
                .coerceAtMost(nowEpoch)
            val songId = session.songId
            val historyEntry = PlaybackStatsRepository.PlaybackHistoryEntry(
                songId = songId,
                timestamp = timestamp
            )
            _playbackHistory.update { current ->
                (listOf(historyEntry) + current).take(MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS)
            }
            persistPlayback(
                songId = songId,
                listened = listened,
                timestamp = timestamp,
                forceSynchronous = forceSynchronousPersistence
            )
            scrobbler.onPlaybackFinished(
                songId = songId,
                listenedMs = listened,
                durationMs = session.totalDurationMs,
                endedAtEpochMs = timestamp
            )
        }
        currentSession = null
        if (pendingVoluntarySongId == session.songId) {
            pendingVoluntarySongId = null
        }
    }

    @Synchronized
    fun onPlaybackStopped() {
        finalizeCurrentSession()
    }

    @Synchronized
    fun onCleared() {
        finalizeCurrentSession(forceSynchronousPersistence = true)
        scope = null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun persistPlayback(
        songId: String,
        listened: Long,
        timestamp: Long,
        forceSynchronous: Boolean
    ) {
        persistenceScope.launch {
            runCatching {
                persistPlaybackInternal(songId = songId, listened = listened, timestamp = timestamp)
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to persist listening session for song=%s", songId)
            }
        }
    }

    private suspend fun persistPlaybackInternal(songId: String, listened: Long, timestamp: Long) {
        dailyMixManager.recordPlay(
            songId = songId,
            songDurationMs = listened,
            timestamp = timestamp
        )
        playbackStatsRepository.recordPlayback(
            songId = songId,
            durationMs = listened,
            timestamp = timestamp
        )
    }

    @Synchronized
    fun setPlaybackSpeed(speed: Float) { playbackSpeed = speed }

    @Synchronized
    fun markEndReason(reason: MixEndReason) {
        val session = currentSession ?: return
        if (session.endReason == MixEndReason.UNKNOWN || reason == MixEndReason.DISLIKE || reason == MixEndReason.ERROR) session.endReason = reason
    }

    @Synchronized
    fun onDiscontinuity(oldPositionMs: Long, newPositionMs: Long, sameAttempt: Boolean) {
        val session = currentSession ?: return
        val now = SystemClock.elapsedRealtime()
        if (sameAttempt) session.exposure.discontinuity(oldPositionMs, newPositionMs, now, session.isPlaying, playbackSpeed)
        else session.exposure.sample(oldPositionMs, now, session.isPlaying, playbackSpeed)
        session.lastKnownPositionMs = newPositionMs
        persistAttempt(session, checkpoint = true)
    }

    private fun sampleExposure(session: ActiveSession, positionMs: Long, now: Long, playing: Boolean) {
        session.exposure.sample(positionMs, now, playing, playbackSpeed)
        if (now - session.lastCheckpointMs >= 15_000 || session.isPlaying != playing) {
            persistAttempt(session, checkpoint = true)
            session.lastCheckpointMs = now
        }
    }

    private fun persistAttempt(session: ActiveSession, checkpoint: Boolean = false) {
        mixLearning.record(MixAttempt(
            id = session.attemptId, songId = session.songId, mixId = session.mixId,
            startedAt = session.startedAtEpochMs, activeMs = session.exposure.activeMs,
            uniqueMs = session.exposure.uniqueMs, repeatedMs = session.exposure.repeatedMs,
            durationMs = session.totalDurationMs, voluntary = session.isVoluntary,
            endReason = if (checkpoint) MixEndReason.UNKNOWN.name else session.endReason.name,
            seeks = session.exposure.seeks, decisionId = session.decisionId,
            sessionId = session.sessionId, recordingId = session.recordingId
        ))
    }

    private fun accumulateRealtimeListening(session: ActiveSession, nowRealtime: Long) {
        if (!session.isPlaying) return
        val delta = (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        if (delta > 0L) {
            session.accumulatedListeningMs += delta
        }
    }

    private fun normalizeDuration(durationMs: Long, fallbackDurationMs: Long): Long {
        return when {
            durationMs > 0 && durationMs != C.TIME_UNSET -> durationMs
            fallbackDurationMs > 0 && fallbackDurationMs != C.TIME_UNSET -> fallbackDurationMs
            else -> 0L
        }
    }

    companion object {
        private val MIN_SESSION_LISTEN_MS = TimeUnit.SECONDS.toMillis(5)
        private const val MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS = 500
    }
}

/**
 * Represents an active listening session for a song.
 */
data class ActiveSession(
    val songId: String,
    var totalDurationMs: Long,
    val startedAtEpochMs: Long,
    var lastKnownPositionMs: Long,
    var accumulatedListeningMs: Long,
    var lastRealtimeMs: Long,
    var lastUpdateEpochMs: Long,
    var isPlaying: Boolean,
    val isVoluntary: Boolean,
    val mixId: String? = null,
    val decisionId: String? = null,
    val sessionId: String? = null,
    val recordingId: String? = null,
    val attemptId: String = java.util.UUID.randomUUID().toString(),
    val exposure: PlaybackExposure = PlaybackExposure(lastKnownPositionMs, lastRealtimeMs, isPlaying),
    var endReason: MixEndReason = MixEndReason.UNKNOWN,
    var lastCheckpointMs: Long = lastRealtimeMs
)
