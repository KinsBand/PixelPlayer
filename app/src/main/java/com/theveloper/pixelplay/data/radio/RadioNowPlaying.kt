package com.theveloper.pixelplay.data.radio

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recognition.RecentlyHeardRepository
import com.theveloper.pixelplay.data.recognition.VoiceSearchResolver
import com.theveloper.pixelplay.presentation.viewmodel.VoiceSearchStateHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A song heard on the radio during this listening session. */
@Immutable
data class RadioHeardTrack(
    val id: String,
    val title: String,
    val artist: String,
    val stationUuid: String?,
    val stationName: String?,
    val heardAt: Long,
    val source: Source,
    /** Cover and library/online id, once the song has been looked up. */
    val coverUrl: String? = null,
    val songId: String? = null,
    /**
     * Where the song starts and ends on the radio timeline (recording ms for a recorded station,
     * listening ms otherwise). End stays null while it's on; it's also capped at the song's length
     * once that's known, so talk after a song shows as a gap.
     */
    val startPosMs: Long? = null,
    val endPosMs: Long? = null,
    /** Listen-identified songs: the start is when it was recognised, not when it began. */
    val approximateStart: Boolean = false,
    /** The song's length, once it has been looked up (0 = unknown). */
    val durationMs: Long = 0L,
) {
    enum class Source {
        /** The station said so in its stream (ICY "StreamTitle" / HLS ID3). */
        STREAM,
        /** Recognised with Listen (Android / Pixel Now Playing). */
        LISTEN,
    }

    val key: String get() = keyOf(title, artist)

    /** As a [Song] for Practice and the song menu. Not playable until looked up. */
    fun toSong(): Song = Song.emptySong().copy(
        id = songId ?: ("heard:" + key),
        title = title,
        artist = artist.ifBlank { "Unknown Artist" },
        albumArtUriString = coverUrl,
    )

    companion object {
        fun keyOf(title: String, artist: String): String =
            (title.trim() + "|" + artist.trim()).lowercase(Locale.ROOT)
    }
}

/**
 * What's on the radio, and what has been on it while you listened:
 *
 *  - **Stream titles.** Most Icecast/Shoutcast stations send the current song in the stream
 *    ("Artist - Title"); HLS stations send ID3 tags. MusicService passes that metadata here, so
 *    songs are captured without any listening at all.
 *  - **Listen.** For stations that don't send titles, Listen asks Android's Now Playing to
 *    identify the song (on-device on Pixels; Google's song search elsewhere), and the answer
 *    from its notification is added.
 *  - **Session history.** Everything captured since you started listening to the radio, newest
 *    first, for the player's History sheet. It ends when something other than radio plays.
 *  - **Listening time** for the player's timeline: how long you've been on this station
 *    (counts only while it plays).
 */
@Singleton
class RadioNowPlaying @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceSearch: dagger.Lazy<VoiceSearchStateHolder>,
    private val recentlyHeard: dagger.Lazy<RecentlyHeardRepository>,
    private val resolver: dagger.Lazy<VoiceSearchResolver>,
    private val timeShift: dagger.Lazy<com.theveloper.pixelplay.data.radio.timeshift.RadioTimeShiftRecorder>,
) {
    sealed interface ListenResult {
        data class Found(val track: RadioHeardTrack, val fromStream: Boolean) : ListenResult
        data object NotFound : ListenResult
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _session = MutableStateFlow<List<RadioHeardTrack>>(emptyList())
    /** Newest first. */
    val session: StateFlow<List<RadioHeardTrack>> = _session.asStateFlow()

    private val _current = MutableStateFlow<RadioHeardTrack?>(null)
    /** What the stream says is on right now (null when it doesn't say). */
    val current: StateFlow<RadioHeardTrack?> = _current.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _results = MutableSharedFlow<ListenResult>(extraBufferCapacity = 4)
    val results: SharedFlow<ListenResult> = _results.asSharedFlow()

    @Volatile private var stationUuid: String? = null
    @Volatile private var stationName: String? = null
    private var listenJob: Job? = null
    private val lookups = Semaphore(1)

    // ---- Radio timeline position --------------------------------------------------------------

    /**
     * The player's position in the current item (ms), set by MusicService; main thread only.
     * With the item's start in the recording it gives the playhead on the recording.
     */
    @Volatile var playerPositionProvider: (() -> Long?)? = null

    /** The recording for the station playing now, if it's being recorded. */
    private fun recording(): com.theveloper.pixelplay.data.radio.timeshift.RadioTimeShiftRecorder.Session? =
        if (stationUuid == null) null else timeShift.get().current()

    /** Where playback is in the recording (ms), or null when this station isn't recorded. */
    fun playheadMs(): Long? {
        val rec = recording() ?: return null
        val pos = playerPositionProvider?.invoke() ?: return null
        return rec.itemStartMs + pos
    }

    /** "Now" on the radio timeline: the playhead in the recording, else listening time. */
    fun timelineNowMs(): Long = playheadMs() ?: listenedMs()

    // Recorded titles become "now playing" when the playhead reaches them (not when they were
    // downloaded, which is earlier), and again when you rewind into them.
    private var titleTicker: Job? = null
    private var appliedTitle: com.theveloper.pixelplay.data.radio.timeshift.RadioTimeShiftRecorder.TitleEvent? = null
    private val titleTracks = HashMap<Long, String>()

    private fun startTitleTicker() {
        if (titleTicker?.isActive == true) return
        titleTicker = scope.launch {
            while (true) {
                applyRecordedTitle()
                kotlinx.coroutines.delay(TITLE_TICK_MS)
            }
        }
    }

    private fun applyRecordedTitle() {
        val events = timeShift.get().titles.value
        if (events.isEmpty()) return
        val head = playheadMs() ?: return
        val event = events.lastOrNull { it.positionMs <= head } ?: return
        if (event == appliedTitle) return
        appliedTitle = event
        val knownId = titleTracks[event.positionMs]
        val known = knownId?.let { id -> _session.value.firstOrNull { it.id == id } }
        if (known != null) {
            _current.value = known
            return
        }
        val parsed = parseStreamTitle(event.raw, stationName)
        if (parsed == null) {
            // Ident, ad or talk: nothing identifiable is on. The song before it ends here.
            closeOpenTrack(event.positionMs)
            _current.value = null
            return
        }
        val track = add(parsed.first, parsed.second, RadioHeardTrack.Source.STREAM, event.positionMs)
        titleTracks[event.positionMs] = track.id
        _current.value = track
    }

    /** The newest song without an end ends at [positionMs]. */
    private fun closeOpenTrack(positionMs: Long) {
        _session.update { list ->
            val newest = list.firstOrNull() ?: return@update list
            if (newest.endPosMs != null || newest.startPosMs == null || positionMs <= newest.startPosMs) return@update list
            listOf(newest.copy(endPosMs = cappedEnd(newest, positionMs))) + list.drop(1)
        }
    }

    /** Ends at [positionMs], or earlier when the song's own length says it finished before. */
    private fun cappedEnd(track: RadioHeardTrack, positionMs: Long): Long {
        val start = track.startPosMs ?: return positionMs
        return if (track.durationMs > 0 && !track.approximateStart) minOf(positionMs, start + track.durationMs) else positionMs
    }

    // Listening time on the current station.
    @Volatile private var accumulatedMs = 0L
    @Volatile private var playingSince = 0L

    /** How long you've been listening to this station, ms (pauses don't count). */
    fun listenedMs(): Long {
        val since = playingSince
        return accumulatedMs + if (since > 0L) SystemClock.elapsedRealtime() - since else 0L
    }

    // ---- From MusicService (main thread) ------------------------------------------------------

    /** The player moved to another item. Radio keeps the session; anything else ends it. */
    fun onMediaItem(mediaId: String?, title: String?, isPlaying: Boolean) {
        val uuid = mediaId?.takeIf { it.startsWith(RadioPlayback.ID_PREFIX) }?.removePrefix(RadioPlayback.ID_PREFIX)
        if (uuid == null) {
            if (stationUuid != null) endSession()
            return
        }
        if (uuid != stationUuid) {
            stationUuid = uuid
            stationName = title
            _current.value = null
            accumulatedMs = 0L
            playingSince = if (isPlaying) SystemClock.elapsedRealtime() else 0L
            appliedTitle = null
            titleTracks.clear()
            startTitleTicker()
        }
    }

    // Paused for a long time on a recorded station: stop downloading (it resumes on play).
    private var pauseSuspendJob: Job? = null

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (stationUuid == null) return
        pauseSuspendJob?.cancel()
        if (!isPlaying) {
            pauseSuspendJob = scope.launch {
                kotlinx.coroutines.delay(com.theveloper.pixelplay.data.radio.timeshift.RadioTimeShift.MAX_REWIND_MS)
                timeShift.get().suspendRecording()
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (isPlaying) {
            if (playingSince == 0L) playingSince = now
        } else if (playingSince > 0L) {
            accumulatedMs += now - playingSince
            playingSince = 0L
        }
    }

    /** Timed metadata from the stream: ICY StreamTitle, or ID3 title/artist on HLS. */
    @OptIn(UnstableApi::class)
    fun onMetadata(mediaId: String?, metadata: Metadata) {
        if (mediaId == null || !mediaId.startsWith(RadioPlayback.ID_PREFIX)) return
        var icyTitle: String? = null
        var id3Title: String? = null
        var id3Artist: String? = null
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is IcyInfo -> icyTitle = entry.title
                is TextInformationFrame -> when (entry.id) {
                    "TIT2" -> id3Title = entry.values.firstOrNull()
                    "TPE1" -> id3Artist = entry.values.firstOrNull()
                }
                else -> Unit
            }
        }
        val parsed = icyTitle?.let { parseStreamTitle(it, stationName) }
            ?: id3Title?.takeIf { isSongLike(it, stationName) }?.let { it.trim() to id3Artist?.trim().orEmpty() }
        val now = timelineNowMs()
        if (parsed == null) {
            // Station ident, ad break or an empty title: nothing identifiable is on.
            if (icyTitle != null) {
                closeOpenTrack(now)
                _current.value = null
            }
            return
        }
        val (title, artist) = parsed
        val track = add(title, artist, RadioHeardTrack.Source.STREAM, now)
        _current.value = track
    }

    // ---- From the player UI -------------------------------------------------------------------

    /**
     * Finds out what's playing. Uses the stream's own title when it sent one in the last few
     * minutes (instant); otherwise asks Now Playing to listen and waits for its answer.
     */
    fun listen() {
        if (_listening.value) return
        val streamNow = _current.value
        if (streamNow != null && System.currentTimeMillis() - streamNow.heardAt < STREAM_TITLE_FRESH_MS) {
            _results.tryEmit(ListenResult.Found(streamNow, fromStream = true))
            return
        }
        _listening.value = true
        listenJob?.cancel()
        listenJob = scope.launch {
            try {
                val heard = async {
                    withTimeoutOrNull(LISTEN_TIMEOUT_MS) { recentlyHeard.get().newlyHeard.first() }
                }
                voiceSearch.get().triggerNowPlayingListen()
                val entry = heard.await()
                if (entry != null) {
                    val track = add(entry.title, entry.artist, RadioHeardTrack.Source.LISTEN, timelineNowMs(), approximate = true)
                    _results.tryEmit(ListenResult.Found(track, fromStream = false))
                } else {
                    _results.tryEmit(ListenResult.NotFound)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "RadioNowPlaying: listen failed")
                _results.tryEmit(ListenResult.NotFound)
            } finally {
                _listening.value = false
            }
        }
    }

    fun cancelListen() {
        listenJob?.cancel()
        _listening.value = false
    }

    fun remove(id: String) {
        _session.update { list -> list.filterNot { it.id == id } }
    }

    fun clearSession() {
        _session.value = emptyList()
    }

    /** The song itself (library first, then online), to play or open. */
    suspend fun findSong(track: RadioHeardTrack): Song? =
        runCatching { resolver.get().findSong(track.title, track.artist) }.getOrNull()

    /** The newest captured song, for "Add to Practice" while radio plays. */
    fun latest(): RadioHeardTrack? = _session.value.firstOrNull()

    // ---- Internals ----------------------------------------------------------------------------

    private fun endSession() {
        stationUuid = null
        stationName = null
        _current.value = null
        titleTicker?.cancel()
        titleTicker = null
        pauseSuspendJob?.cancel()
        appliedTitle = null
        titleTracks.clear()
        // Leaving radio: the recording can't be rewound into any more, so it's deleted.
        timeShift.get().stop()
        _session.value = emptyList()
        accumulatedMs = 0L
        playingSince = 0L
        cancelListen()
    }

    /** Adds a song unless it's the same as the newest one (stations repeat titles). */
    private fun add(
        title: String,
        artist: String,
        source: RadioHeardTrack.Source,
        positionMs: Long? = null,
        approximate: Boolean = false,
    ): RadioHeardTrack {
        val key = RadioHeardTrack.keyOf(title, artist)
        val newest = _session.value.firstOrNull()
        if (newest != null && newest.key == key && System.currentTimeMillis() - newest.heardAt < SAME_SONG_WINDOW_MS) {
            // Same song still on (stations repeat titles): it keeps its first start.
            return newest
        }
        // A new song: the one before it ends here (if the station didn't send a gap first).
        if (positionMs != null) closeOpenTrack(positionMs)
        val earlier = _session.value.firstOrNull { it.key == key }
        val track = RadioHeardTrack(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            stationUuid = stationUuid,
            stationName = stationName,
            heardAt = System.currentTimeMillis(),
            source = source,
            coverUrl = earlier?.coverUrl,
            songId = earlier?.songId,
            startPosMs = positionMs,
            approximateStart = approximate,
            durationMs = earlier?.durationMs ?: 0L,
        )
        _session.update { (listOf(track) + it).take(MAX_SESSION) }
        if (track.coverUrl == null) lookUp(track)
        return track
    }

    /** Finds a cover (and the song) in the background, one at a time. */
    private fun lookUp(track: RadioHeardTrack) {
        scope.launch {
            lookups.withPermit {
                if (_session.value.none { it.id == track.id }) return@withPermit
                val song = findSong(track) ?: return@withPermit
                val length = song.duration.coerceAtLeast(0L)
                _session.update { list ->
                    list.map {
                        if (it.key == track.key && it.songId == null) {
                            val found = it.copy(coverUrl = song.albumArtUriString ?: it.coverUrl, songId = song.id, durationMs = length)
                            // Already ended: now that the length is known, the talk after it is a gap.
                            found.endPosMs?.let { end -> found.copy(endPosMs = cappedEnd(found, end)) } ?: found
                        } else {
                            it
                        }
                    }
                }
                if (_current.value?.id == track.id) {
                    _current.value = _current.value?.copy(coverUrl = song.albumArtUriString, songId = song.id, durationMs = length)
                }
            }
        }
    }

    companion object {
        private const val MAX_SESSION = 200
        private const val SAME_SONG_WINDOW_MS = 15 * 60_000L
        private const val STREAM_TITLE_FRESH_MS = 6 * 60_000L
        private const val LISTEN_TIMEOUT_MS = 35_000L
        private const val TITLE_TICK_MS = 500L

        private val JUNK = Regex(
            "(advert|commercial break|jingle|sponsor|station id|^news( update| headlines)?$|^(traffic|weather)( report| update)?$|^unknown$|^untitled$|on air now|^-+$|https?://|www\\.)",
            RegexOption.IGNORE_CASE
        )

        /**
         * "Artist - Title" (the Icecast convention) → (title, artist). Null for station idents,
         * ads, URLs and titles that are just the station's name.
         */
        fun parseStreamTitle(raw: String, stationName: String?): Pair<String, String>? {
            val text = raw.trim().trim('\'', '"').replace(Regex("\\s+"), " ")
            if (!isSongLike(text, stationName)) return null
            val sep = listOf(" - ", " – ", " — ", " ~ ", " | ").firstNotNullOfOrNull { s -> text.indexOf(s).takeIf { it > 0 }?.let { it to s.length } }
            if (sep == null) return null // a single phrase is usually a show or slogan, not a song
            val artist = text.substring(0, sep.first).trim()
            val title = text.substring(sep.first + sep.second).trim()
                .replace(Regex("\\s*\\((?:\\d{4}|radio edit|clean)\\)$", RegexOption.IGNORE_CASE), "")
            if (artist.isEmpty() || title.isEmpty()) return null
            if (stationName != null && (artist.equals(stationName, true) || title.equals(stationName, true))) return null
            return title to artist
        }

        private fun isSongLike(text: String, stationName: String?): Boolean {
            val t = text.trim()
            if (t.length < 3 || t.length > 160) return false
            if (JUNK.containsMatchIn(t)) return false
            if (stationName != null && t.equals(stationName.trim(), ignoreCase = true)) return false
            return t.any { it.isLetter() }
        }

        /** For Compose code, which can't take constructor injection. */
        fun get(context: Context): RadioNowPlaying =
            EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).radioNowPlaying()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun radioNowPlaying(): RadioNowPlaying
    }
}
