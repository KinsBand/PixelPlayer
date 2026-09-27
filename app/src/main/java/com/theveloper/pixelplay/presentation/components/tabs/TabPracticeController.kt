package com.theveloper.pixelplay.presentation.components.tabs

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.songsterr.RenderedTrack
import com.theveloper.pixelplay.data.songsterr.SongsterrClient
import com.theveloper.pixelplay.data.songsterr.SongsterrJson
import com.theveloper.pixelplay.data.songsterr.TabLayout
import com.theveloper.pixelplay.data.songsterr.TabMidi
import com.theveloper.pixelplay.data.songsterr.TabParser
import com.theveloper.pixelplay.data.songsterr.TabSection
import com.theveloper.pixelplay.data.songsterr.TabTimeline
import com.theveloper.pixelplay.data.youtube.YouTubeStreamExtractor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

sealed interface TabUiState {
    data object Loading : TabUiState
    data class Ready(
        val tab: SongsterrClient.LoadedTab,
        val track: RenderedTrack,
        val sections: List<TabSection>,
        val timeline: TabTimeline,
    ) : TabUiState
    data class Failed(val message: String, val songUrl: String?) : TabUiState
}

enum class TabSound { ORIGINAL, SYNTH }

/** Lets the tab screen reach the app's YouTube stream resolver (for backing / solo videos). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TabAudioEntryPoint {
    fun youTubeStreamExtractor(): YouTubeStreamExtractor
}

/**
 * Everything the Instruments page needs: loading a part, the song's other parts, and practice
 * playback.
 *
 * - ORIGINAL plays the real song through the app's player (speed and pitch change it). Muting
 *   parts swaps to one of Songsterr's synced YouTube backing ("without drums") or solo videos
 *   when one exists.
 * - SYNTH plays every part through Android's General MIDI synth; any part can be muted, and
 *   holding mute solos a part.
 * Loops, count-in and metronome work in both.
 */
@Stable
class TabPracticeController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("songsterr_tabs", Context.MODE_PRIVATE)

    // ── Song and part ──
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var playingSongId by mutableStateOf<String?>(null)
        private set
    var state by mutableStateOf<TabUiState>(TabUiState.Loading)
        internal set
    var requestedTrack by mutableStateOf<Int?>(null)
        internal set
    var reloadKey by mutableStateOf(0)
        internal set
    var transpose by mutableStateOf(0)
        private set
    var expandedSections by mutableStateOf<Set<Int>>(emptySet())
    var pdfUri by mutableStateOf<Uri?>(null)

    /** Every playable part of the song (for SYNTH), by track index. */
    var parts by mutableStateOf<Map<Int, TabMidi.Part>>(emptyMap())
        private set
    var muted by mutableStateOf<Set<Int>>(emptySet())
        private set

    internal var loaded: SongsterrClient.LoadedTab? = null
    private var mainPoints: List<Double>? = null
    private var videos: List<SongsterrJson.VideoPoints> = emptyList()
    private var rawParts: Map<Int, com.theveloper.pixelplay.data.songsterr.model.RevisionTrack> = emptyMap()

    // ── Practice ──
    var sound by mutableStateOf(TabSound.ORIGINAL)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var speed by mutableStateOf(1f)
        private set
    var pitch by mutableStateOf(0)
        private set
    var metronome by mutableStateOf(false)
        private set
    var countIn by mutableStateOf(false)
    /** Count-in length in beats; null = one bar. */
    var countInBeats by mutableStateOf<Int?>(null)
    var loopSelecting by mutableStateOf(false)
        private set
    var loopFrom by mutableStateOf<Int?>(null)
        private set
    var loopTo by mutableStateOf<Int?>(null)
        private set
    private var offsetState by mutableIntStateOf(0)
    /**
     * Moves the tab against the song (ms; + = the tab plays later). Saved per song and part, so
     * a fix (by hand or from the drum kit's timing suggestion) sticks.
     */
    var syncOffsetMs: Int
        get() = offsetState
        set(value) {
            offsetState = value.coerceIn(-10_000, 10_000)
            offsetKey()?.let { prefs.edit().putInt(it, offsetState).apply() }
        }
    /** Changes on every jump (tap on a bar, loop start, restart): the cursor snaps instead of gliding. */
    var seekSerial by mutableIntStateOf(0)
        private set
    /** Where the music will be one beat from now (the page starts scrolling to the next line early). */
    var lookahead by mutableStateOf<TabTimeline.Position?>(null)
        private set
    /** Electronic drum kit: connection, pad map, scoring. */
    val drums = DrumSession(context)
    var cursor by mutableStateOf<TabTimeline.Position?>(null)
        private set
    var panelExpanded by mutableStateOf(false)
    /**
     * SYNTH plays guitar and bass on modelled strings ([StringSynthPlayer]) instead of the
     * General MIDI synth. Saved in prefs `real_strings`.
     */
    var realStrings by mutableStateOf(prefs.getBoolean("real_strings", true))
        private set
    /** Parts (track index) with a note sounding right now, for the live speaker animation. */
    var soundingParts by mutableStateOf<Set<Int>>(emptySet())
        private set
    var countingIn by mutableStateOf(false)
        private set
    /** Every part of the song, known before any part is loaded (for the instrument picker). */
    var meta by mutableStateOf<SongsterrJson.SongMeta?>(null)
        private set
    /** The instruments sheet is open. */
    var picking by mutableStateOf(false)
        private set
    /** The song's parts are known but none has been chosen yet (no default instrument). */
    var awaitingPick by mutableStateOf(false)
        private set
    /** A Songsterr listing / version picked by hand in the instruments sheet (null = automatic). */
    var source by mutableStateOf<SongsterrClient.TabSource?>(null)
        private set
    /** Other Songsterr listings of this song; null until asked for. */
    var alternatives by mutableStateOf<List<SongsterrClient.Alternative>?>(null)
        private set
    var alternativesLoading by mutableStateOf(false)
        private set
    /** Every saved version of the current listing, newest first; null until loaded. */
    var revisions by mutableStateOf<List<SongsterrJson.Revision>?>(null)
        private set
    var revisionsLoading by mutableStateOf(false)
        private set
    /** Instrument opened straight away for every song (skips the picker), or null. */
    var defaultFamily by mutableStateOf(
        runCatching { prefs.getString("default_family", null)?.let { TabParser.InstrumentFamily.valueOf(it) } }.getOrNull(),
    )
        private set
    /** The playing song's own file, used for gapless loops of the original audio. */
    private var songUri: String? = null
    /** YouTube backing/solo video playing instead of the song (ORIGINAL with parts muted). */
    var backingVideo by mutableStateOf<SongsterrJson.VideoPoints?>(null)
        private set

    val songUrl: String?
        get() = when (val s = state) {
            is TabUiState.Ready -> s.tab.songUrl
            is TabUiState.Failed -> s.songUrl
            TabUiState.Loading -> null
        }

    val loopActive: Boolean get() = loopFrom != null && loopTo != null

    /** Songsterr song id of the listing on screen. */
    val currentSongId: Long? get() = loaded?.meta?.songId ?: meta?.songId
    /** Revision on screen. */
    val currentRevisionId: Long? get() = loaded?.meta?.revisionId ?: meta?.revisionId
    /** Who made the tab on screen, when Songsterr says. */
    val tabAuthor: String?
        get() = (loaded?.meta ?: meta)?.author
            ?: revisions?.firstOrNull { it.revisionId == currentRevisionId }?.author

    private val ready: TabUiState.Ready? get() = state as? TabUiState.Ready

    /** The part on screen is a drum part. */
    val isDrumsView: Boolean get() = ready?.track?.isDrums == true

    fun setSong(title: String, artist: String, songId: String?, uri: String? = null) {
        songUri = uri
        if (title == this.title && artist == this.artist) return
        stopSynth()
        stopLooper(handBack = false)
        stopBacking(resumeMain = false)
        meta = null
        picking = false
        awaitingPick = false
        source = null
        alternatives = null
        revisions = null
        this.title = title
        this.artist = artist
        this.playingSongId = songId
        requestedTrack = null
        loaded = null
        mainPoints = null
        videos = emptyList()
        rawParts = emptyMap()
        parts = emptyMap()
        muted = emptySet()
        clearLoop()
        cursor = null
        pdfUri = null
        state = TabUiState.Loading
    }

    /** Show another part's tab. */
    fun selectTrack(index: Int) {
        picking = false
        awaitingPick = false
        (meta ?: loaded?.meta)?.tracks?.getOrNull(index)?.let { t ->
            prefs.edit().putString("family", t.family.name).apply()
        }
        if (index == loaded?.trackIndex && state is TabUiState.Ready) return
        stopSynth()
        clearLoop()
        requestedTrack = index
        reloadKey++
    }

    /** Show the instrument list in place of the tab. */
    fun openPicker() {
        if (meta == null && loaded == null) return
        if (meta == null) meta = loaded?.meta
        panelExpanded = false
        picking = true
    }

    /** Close the instruments sheet (back arrow, swipe down, or back). */
    fun closePicker() {
        picking = false
    }

    /** Look up the song's other Songsterr listings (once per song). */
    fun loadAlternatives(scope: CoroutineScope) {
        if (alternatives != null || alternativesLoading) return
        alternativesLoading = true
        scope.launch {
            alternatives = runCatching { SongsterrClient.searchAlternatives(title, artist) }.getOrDefault(emptyList())
            alternativesLoading = false
        }
    }

    /** Look up every version of the listing on screen. */
    fun loadRevisions(scope: CoroutineScope) {
        if (revisions != null || revisionsLoading) return
        val id = currentSongId ?: return
        revisionsLoading = true
        scope.launch {
            revisions = runCatching { SongsterrClient.fetchRevisions(id, appContext.cacheDir) }.getOrDefault(emptyList())
            revisionsLoading = false
        }
    }

    /** Show another Songsterr listing of the song (latest version). */
    fun chooseListing(songId: Long) {
        if (songId == currentSongId && source?.revisionId == null) return
        switchTabSource(SongsterrClient.TabSource(songId = songId))
    }

    /** Show an older (or the latest) version of the listing on screen. */
    fun chooseRevision(revision: SongsterrJson.Revision) {
        val id = currentSongId ?: return
        if (revision.revisionId == currentRevisionId) return
        val latest = revisions?.firstOrNull()?.revisionId
        switchTabSource(
            SongsterrClient.TabSource(
                songId = id,
                revisionId = revision.revisionId.takeIf { it != latest },
                revisionImage = revision.image,
            ),
        )
    }

    /** Back to the automatic match (latest version), e.g. after a picked tab fails to load. */
    fun resetSource() {
        if (source == null) return
        switchTabSource(null)
    }

    private fun switchTabSource(next: SongsterrClient.TabSource?) {
        val sameSong = next?.songId != null && next.songId == currentSongId
        stopSynth()
        stopLooper(handBack = true)
        stopBacking(resumeMain = backing != null && isPlaying)
        source = next
        loaded = null
        meta = null
        mainPoints = null
        videos = emptyList()
        rawParts = emptyMap()
        parts = emptyMap()
        muted = emptySet()
        clearLoop()
        cursor = null
        pdfUri = null
        requestedTrack = null
        if (!sameSong) revisions = null
        // Keep the sheet open on the new listing's parts.
        picking = true
        state = TabUiState.Loading
        reloadKey++
    }

    /** Make [family] open straight away for every song; tap again to go back to asking. */
    fun toggleDefault(family: TabParser.InstrumentFamily) {
        defaultFamily = if (defaultFamily == family) null else family
        prefs.edit().putString("default_family", defaultFamily?.name).apply()
    }

    private fun familyOf(t: SongsterrJson.MetaTrack): TabParser.InstrumentFamily = when {
        t.isDrums -> TabParser.InstrumentFamily.DRUMS
        t.isBass -> TabParser.InstrumentFamily.BASS
        t.isVocal -> TabParser.InstrumentFamily.OTHER
        else -> TabParser.InstrumentFamily.GUITAR
    }

    /** The family a picker row stands for (drums, bass, guitar…). */
    fun familyFor(t: SongsterrJson.MetaTrack): TabParser.InstrumentFamily = familyOf(t)

    fun retry() {
        reloadKey++
    }

    private fun preferredFamily(): TabParser.InstrumentFamily =
        runCatching { TabParser.InstrumentFamily.valueOf(prefs.getString("family", null) ?: "GUITAR") }
            .getOrDefault(TabParser.InstrumentFamily.GUITAR)

    /** Fetches the part (network or cache), then the song's other parts in the background. */
    suspend fun load() {
        if (title.isBlank() && artist.isBlank()) {
            state = TabUiState.Failed("Nothing is playing.", null)
            return
        }
        state = TabUiState.Loading
        val track = requestedTrack
        val default = defaultFamily
        if (track == null && default == null) {
            // No default instrument: list the song's parts where the tab goes and let the user pick.
            SongsterrClient.loadMeta(title, artist, appContext.cacheDir, source)
                .onSuccess { (m, _) ->
                    meta = m
                    awaitingPick = true
                    picking = true
                    fillRevisions(m.songId)
                }
                .onFailure { e ->
                    state = TabUiState.Failed(
                        e.message ?: "Couldn't load this tab.",
                        (e as? SongsterrClient.TabLoadException)?.songUrl,
                    )
                }
            return
        }
        val family = default ?: preferredFamily()
        val result = SongsterrClient.loadTab(
            title, artist,
            // A picked track index refers to the plain search result, so don't bias the search then.
            preferDrums = track == null && family == TabParser.InstrumentFamily.DRUMS,
            trackIndex = track,
            cacheDir = appContext.cacheDir,
            family = family,
            source = source,
        )
        result.onSuccess { tab ->
            loaded = tab
            restoreOffset()
            if (meta == null) meta = tab.meta
            if (videos.isEmpty()) {
                videos = runCatching {
                    SongsterrClient.fetchVideoPoints(tab.meta.songId, tab.meta.revisionId, appContext.cacheDir)
                }.getOrDefault(emptyList())
                mainPoints = SongsterrClient.pickVideoPoints(videos, playingSongId)?.points
            }
            rebuild()
            fillRevisions(tab.meta.songId)
            loadAllParts(tab)
        }.onFailure { e ->
            state = TabUiState.Failed(
                e.message ?: "Couldn't load this tab.",
                (e as? SongsterrClient.TabLoadException)?.songUrl,
            )
        }
    }

    /** Loads the version list once (it also names the tab's author when the meta doesn't). */
    private suspend fun fillRevisions(songId: Long) {
        if (revisions != null) return
        revisions = runCatching { SongsterrClient.fetchRevisions(songId, appContext.cacheDir) }.getOrNull()
    }

    /** Re-renders the loaded part (after a transpose change). */
    suspend fun rebuild() {
        val tab = loaded ?: return
        val t = transpose
        val points = backingVideo?.points ?: mainPoints
        val newReady = withContext(Dispatchers.Default) {
            val rendered = TabParser.toRenderModel(tab.track, tab.family, t)
            TabUiState.Ready(tab, rendered, TabLayout.sections(rendered), TabTimeline(rendered, points))
        }
        state = newReady
        drums.setScore(newReady.track)
        if (rawParts.isNotEmpty()) rebuildParts()
    }

    private suspend fun loadAllParts(tab: SongsterrClient.LoadedTab) {
        if (rawParts.isNotEmpty()) {
            rebuildParts()
            return
        }
        val meta = tab.meta
        val loadedParts = coroutineScope {
            meta.tracks.filter { !it.isEmpty }.map { t ->
                async(Dispatchers.IO) {
                    if (t.index == tab.trackIndex) return@async t.index to tab.track
                    val json = SongsterrClient.fetchPartJson(meta, t.index, appContext.cacheDir) ?: return@async null
                    SongsterrJson.parseTrack(json)?.let { t.index to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        rawParts = loadedParts
        rebuildParts()
    }

    private suspend fun rebuildParts() {
        val meta = loaded?.meta ?: return
        val t = transpose
        val m = muted
        parts = withContext(Dispatchers.Default) {
            rawParts.mapNotNull { (index, raw) ->
                val mt = meta.tracks.getOrNull(index) ?: return@mapNotNull null
                val family = when {
                    mt.isDrums -> TabParser.InstrumentFamily.DRUMS
                    mt.isBass -> TabParser.InstrumentFamily.BASS
                    else -> TabParser.InstrumentFamily.GUITAR
                }
                index to TabMidi.Part(TabParser.toRenderModel(raw, family, t), muted = index in m, isVocal = mt.isVocal)
            }.toMap()
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Mute / solo
    // ═════════════════════════════════════════════════════════════════════

    private fun backingFor(mutedSet: Set<Int>): SongsterrJson.VideoPoints? {
        if (mutedSet.isEmpty()) return null
        val all = loaded?.meta?.tracks?.filter { !it.isEmpty }?.map { it.index }?.toSet() ?: return null
        val playing = all - mutedSet
        if (playing.size == 1) {
            val only = playing.first()
            videos.filter { it.feature == "solo" && only in it.tracks }.minByOrNull { it.tracks.size }?.let { return it }
        }
        return videos.filter { it.feature == "backing" && it.tracks.containsAll(mutedSet) }.minByOrNull { it.tracks.size }
    }

    /** Can [index] be muted with the original audio (a backing video without it exists)? */
    fun canMuteInOriginal(index: Int): Boolean =
        videos.any { it.feature == "backing" && index in it.tracks }

    fun canSoloInOriginal(index: Int): Boolean =
        videos.any { it.feature == "solo" && index in it.tracks }

    fun toggleMute(index: Int, scope: CoroutineScope) {
        applyMutes(if (index in muted) muted - index else muted + index, scope)
    }

    /** Hold on a mute button: mute every other part (tap again on any to unmute all). */
    fun solo(index: Int, scope: CoroutineScope) {
        val all = loaded?.meta?.tracks?.filter { !it.isEmpty }?.map { it.index }?.toSet() ?: return
        val soloSet = all - index
        applyMutes(if (muted == soloSet) emptySet() else soloSet, scope)
    }

    fun unmuteAll(scope: CoroutineScope) = applyMutes(emptySet(), scope)

    private fun applyMutes(target: Set<Int>, scope: CoroutineScope) {
        if (sound == TabSound.ORIGINAL) {
            val video = backingFor(target)
            if (target.isNotEmpty() && video == null) {
                Toast.makeText(
                    appContext,
                    "No version of the original without that part — switch to Synth to mute it",
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            // A backing video can take out more than asked (e.g. every guitar); show that.
            val previous = muted
            muted = if (video == null) emptySet() else if (video.feature == "solo") {
                (loaded?.meta?.tracks?.filter { !it.isEmpty }?.map { it.index }?.toSet() ?: emptySet()) - video.tracks.toSet()
            } else {
                target + video.tracks
            }
            scope.launch { if (!switchOriginalSource(video)) muted = previous }
        } else {
            muted = target
            scope.launch {
                rebuildParts()
                restartSynthIfPlaying()
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Audio
    // ═════════════════════════════════════════════════════════════════════

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var mediaPlayer: MediaPlayer? = null
    /** Guitar / bass on modelled strings, next to the MIDI player (SYNTH with [realStrings]). */
    private var stringPlayer: StringSynthPlayer? = null
    private var synthResult: TabMidi.Result? = null
    private var synthStartEntry = 0
    private var soundPool: SoundPool? = null
    private var clickAccent = 0
    private var clickNormal = 0
    private var lastBeatKey = -1L
    private var lastSeekSeen = 0
    private var wasPlayingTick = false
    private var lastTickEntry = -1
    private var lastTickF = 0f
    private var lastWraps = 0
    private var paramsTouched = false
    private var backing: ExoPlayer? = null
    private var backingUrl: String? = null
    /** Plays a loop of the original audio gaplessly (a clipped copy of the song on repeat). */
    private var looper: ExoPlayer? = null
    private var looperClipStartMs = 0L
    private var looperRange: Pair<Int, Int>? = null
    private val clock = SmoothClock()
    /** Fade-in after a seek-based loop wrap (when no gapless looper is possible). */
    private var fadeStartAt = 0L

    private fun offsetKey(): String? {
        val song = loaded?.meta?.songId ?: return null
        val part = loaded?.trackIndex ?: return null
        return "tab_offset_${song}_$part"
    }

    private fun restoreOffset() {
        offsetState = offsetKey()?.let { prefs.getInt(it, 0) } ?: 0
    }

    /** The drum kit's "tab seems off" suggestion was accepted: shift the tab. */
    fun applyTimingSuggestion() {
        val s = drums.acceptSuggestion() ?: return
        syncOffsetMs += s
        if (looper != null) {
            // The loop player's clip is cut at the old timing: re-cut it.
            stopLooper(handBack = true)
            startLooper()
        }
        clock.reset()
    }

    /** Stops playback (the tap test needs quiet). */
    fun pauseAll() {
        if (sound == TabSound.SYNTH) {
            if (mediaPlayer?.isPlaying == true) {
                synthStartEntry = cursor?.entry ?: synthStartEntry
                stopSynth()
            }
        } else {
            origPause()
        }
    }

    fun attach() {
        drums.start()
        if (controllerFuture != null) return
        runCatching {
            val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = future
            future.addListener({
                controller = runCatching { future.get() }.getOrNull()
                applyOriginalParams()
            }, ContextCompat.getMainExecutor(appContext))
        }.onFailure { Log.w(TAG, "No media controller: ${it.message}") }
        prepareClicks()
    }

    fun detach() {
        drums.stop()
        stopSynth()
        stopLooper(handBack = true)
        stopBacking(resumeMain = false)
        resetOriginalParams()
        if (fadeStartAt != 0L) runCatching { controller?.volume = 1f }
        fadeStartAt = 0L
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        soundPool?.release()
        soundPool = null
    }

    private fun params() = PlaybackParameters(speed, 2f.pow(pitch / 12f))

    private fun applyOriginalParams() {
        backing?.playbackParameters = params()
        looper?.playbackParameters = params()
        val c = controller ?: return
        if (sound != TabSound.ORIGINAL || backing != null) return
        if (speed == 1f && pitch == 0 && !paramsTouched) return
        paramsTouched = true
        runCatching { c.playbackParameters = params() }
    }

    private fun resetOriginalParams() {
        if (!paramsTouched) return
        runCatching { controller?.playbackParameters = PlaybackParameters(1f, 1f) }
        paramsTouched = false
    }

    fun changeSpeed(value: Float) {
        speed = value.coerceIn(0.1f, 2f)
        applyOriginalParams()
        restartSynthIfPlaying()
    }

    fun changePitch(semitones: Int) {
        pitch = semitones.coerceIn(-12, 12)
        applyOriginalParams()
        restartSynthIfPlaying()
    }

    fun changeTranspose(semitones: Int, scope: CoroutineScope) {
        transpose = semitones.coerceIn(-12, 12)
        scope.launch {
            rebuild()
            restartSynthIfPlaying()
        }
    }

    fun toggleRealStrings() {
        realStrings = !realStrings
        prefs.edit().putBoolean("real_strings", realStrings).apply()
        restartSynthIfPlaying()
    }

    fun toggleMetronome() {
        metronome = !metronome
        restartSynthIfPlaying()
    }

    fun changeSound(target: TabSound, scope: CoroutineScope) {
        if (target == sound) return
        seekSerial++
        val wasPlaying = isPlaying
        val entry = cursor?.entry
        if (target == TabSound.SYNTH) {
            origPause()
            stopLooper(handBack = false)
            stopBacking(resumeMain = false)
            if (fadeStartAt != 0L) runCatching { controller?.volume = 1f }
            fadeStartAt = 0L
            resetOriginalParams()
            sound = TabSound.SYNTH
            synthStartEntry = entry ?: 0
            scope.launch {
                rebuild() // back to the main recording's timing
                rebuildParts()
                if (wasPlaying) playSynth(synthStartEntry, withCountIn = false)
            }
        } else {
            stopSynth()
            sound = TabSound.ORIGINAL
            val keep = muted
            muted = emptySet()
            applyOriginalParams()
            val tl = ready?.timeline
            if (entry != null && tl != null) controller?.seekTo((tl.entries[entry].startMs + syncOffsetMs).toLong().coerceAtLeast(0))
            if (wasPlaying) controller?.play()
            if (keep.isNotEmpty()) applyMutes(keep, scope)
            if (loopActive) startLooper()
        }
    }

    // ── Original audio: the app's player, or a backing / solo video ──

    private fun origTimeline(): TabTimeline? = ready?.timeline

    /** Song-file ms minus tab ms for the audio playing (the sync offset applies to the app's player only). */
    private fun mediaOffset(): Double = if (backing != null) 0.0 else syncOffsetMs.toDouble()

    private fun origTabMs(): Double? {
        val l = looper
        if (l != null) return (looperClipStartMs + l.currentPosition).toDouble() - mediaOffset()
        val b = backing
        if (b != null) return b.currentPosition.toDouble()
        val c = controller ?: return null
        return (c.currentPosition - syncOffsetMs).toDouble()
    }

    private fun origIsPlaying(): Boolean = looper?.let { it.isPlaying || it.playWhenReady } ?: backing?.isPlaying ?: controller?.isPlaying ?: false

    private fun origSeekTab(ms: Double) {
        val l = looper
        if (l != null) {
            val rel = (ms + mediaOffset() - looperClipStartMs).toLong()
            val len = l.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            if (rel in 0 until len) {
                l.seekTo(rel)
                return
            }
            // Jumping out of the loop ends it; the song carries on from there.
            val wasPlaying = l.isPlaying || l.playWhenReady
            loopFrom = null
            loopTo = null
            stopLooper(handBack = false)
            val b = backing
            if (b != null) b.seekTo(ms.toLong().coerceAtLeast(0)) else controller?.seekTo((ms + syncOffsetMs).toLong().coerceAtLeast(0))
            if (wasPlaying) origPlay()
            return
        }
        val b = backing
        if (b != null) b.seekTo(ms.toLong().coerceAtLeast(0)) else controller?.seekTo((ms + syncOffsetMs).toLong().coerceAtLeast(0))
    }

    private fun origPlay() {
        looper?.let {
            it.play()
            return
        }
        val b = backing
        if (b != null) b.play() else controller?.play()
    }

    private fun origPause() {
        looper?.pause()
        backing?.pause()
        controller?.pause()
    }

    /** Returns false if the backing video couldn't be loaded. */
    private suspend fun switchOriginalSource(video: SongsterrJson.VideoPoints?): Boolean {
        if (video?.videoId == backingVideo?.videoId) return true
        val hadLooper = looper != null
        stopLooper(handBack = true)
        val ok = switchSource(video)
        if (hadLooper && loopActive) startLooper()
        return ok
    }

    private suspend fun switchSource(video: SongsterrJson.VideoPoints?): Boolean {
        val tl = origTimeline()
        val pos = origTabMs()?.let { tl?.locate(it) }
        val wasPlaying = origIsPlaying()
        if (video == null) {
            stopBacking(resumeMain = false)
            rebuild()
            val newTl = ready?.timeline
            if (pos != null && newTl != null) controller?.seekTo((newTl.msAt(pos.entry, pos.tick) + syncOffsetMs).toLong().coerceAtLeast(0))
            applyOriginalParams()
            if (wasPlaying) controller?.play()
            return true
        }
        val url = runCatching {
            EntryPointAccessors.fromApplication(appContext, TabAudioEntryPoint::class.java)
                .youTubeStreamExtractor()
                .getStreamUrl(video.videoId)
        }.getOrNull()
        if (url == null) {
            Toast.makeText(appContext, "Couldn't load that version of the song", Toast.LENGTH_SHORT).show()
            return false
        }
        controller?.pause()
        stopBacking(resumeMain = false)
        backingVideo = video
        backingUrl = url
        rebuild() // cursor follows the backing video's own sync points
        val player = ExoPlayer.Builder(appContext).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            setMediaItem(MediaItem.fromUri(url))
            playbackParameters = params()
            prepare()
        }
        backing = player
        val newTl = ready?.timeline
        if (pos != null && newTl != null) player.seekTo(newTl.msAt(pos.entry, pos.tick).toLong().coerceAtLeast(0))
        if (wasPlaying) player.play()
        return true
    }

    private fun stopBacking(resumeMain: Boolean) {
        backing?.let {
            runCatching { it.stop() }
            it.release()
        }
        backing = null
        backingUrl = null
        if (backingVideo != null) backingVideo = null
        if (resumeMain) controller?.play()
    }

    // ── Gapless loop of the original ──

    private fun loopSourceUri(): Uri? {
        backingUrl?.let { return Uri.parse(it) }
        if (backing != null) return null
        val u = songUri?.takeIf { it.isNotBlank() } ?: return null
        return when {
            u.startsWith("/") -> Uri.fromFile(File(u))
            u.startsWith("content://") || u.startsWith("file://") || u.startsWith("http://") || u.startsWith("https://") -> Uri.parse(u)
            else -> null
        }
    }

    /**
     * Plays the loop from a clipped copy of the audio set to repeat, so each pass flows into the
     * next with no seek gap. It takes over from the song once it has buffered.
     * Returns false when the audio can't be looped this way (seek-based looping is used then).
     */
    private fun startLooper(): Boolean {
        if (sound != TabSound.ORIGINAL) return false
        val tl = origTimeline() ?: return false
        val range = loopRange(tl) ?: return false
        if (looper != null && looperRange == range) return true
        val uri = loopSourceUri() ?: return false
        val off = mediaOffset()
        val startMs = (tl.entries[range.first].startMs + off).toLong().coerceAtLeast(0)
        val endMs = (tl.entries[range.second].endMs + off).toLong()
        if (endMs - startMs < 200) return false
        val keepPos = origTabMs()
        val wasPlaying = origIsPlaying()
        stopLooper(handBack = false)
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build(),
            )
            .build()
        val player = runCatching {
            ExoPlayer.Builder(appContext).build().apply {
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                )
                setMediaItem(item)
                repeatMode = Player.REPEAT_MODE_ONE
                playbackParameters = params()
                prepare()
            }
        }.getOrElse {
            Log.w(TAG, "Loop player failed: ${it.message}")
            return false
        }
        looper = player
        looperClipStartMs = startMs
        looperRange = range
        fun relFor(tabMs: Double?): Long {
            val rel = ((tabMs ?: 0.0) + off - startMs).toLong()
            return if (rel in 0 until (endMs - startMs)) rel else 0L
        }
        player.seekTo(relFor(keepPos))
        // Hand over once buffered, at the song's position right then.
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_READY) return
                player.removeListener(this)
                if (looper !== player) return
                val mainPlaying = backing?.isPlaying ?: controller?.isPlaying ?: false
                val now = (backing?.currentPosition?.toDouble() ?: controller?.currentPosition?.toDouble())?.minus(off)
                if (mainPlaying && now != null) player.seekTo(relFor(now))
                backing?.pause()
                controller?.pause()
                if (mainPlaying || wasPlaying) player.play()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w(TAG, "Loop player error: ${error.message}")
                if (looper === player) stopLooper(handBack = false)
            }
        })
        return true
    }

    /** Stops the loop player; with [handBack] the song carries on from where the loop was. */
    private fun stopLooper(handBack: Boolean) {
        val l = looper ?: return
        val pos = (looperClipStartMs + l.currentPosition).toDouble() - mediaOffset()
        val wasPlaying = l.isPlaying || l.playWhenReady
        looper = null
        looperRange = null
        runCatching { l.stop() }
        l.release()
        clock.reset()
        if (handBack) {
            val b = backing
            if (b != null) b.seekTo(pos.toLong().coerceAtLeast(0)) else controller?.seekTo((pos + syncOffsetMs).toLong().coerceAtLeast(0))
            if (wasPlaying) {
                if (b != null) b.play() else controller?.play()
            }
        }
    }

    // ── Transport (the sheet's play button routes here on the Instruments page) ──

    fun togglePlay(scope: CoroutineScope) {
        val tl = ready?.timeline ?: return
        if (countingIn) return
        if (sound == TabSound.SYNTH) {
            if (mediaPlayer?.isPlaying == true) {
                synthStartEntry = cursor?.entry ?: synthStartEntry
                stopSynth()
            } else {
                controller?.pause()
                val from = if (loopActive) loopRange(tl)?.first ?: synthStartEntry else synthStartEntry
                playSynth(from, withCountIn = countIn)
            }
            return
        }
        if (origIsPlaying()) {
            origPause()
            return
        }
        loopRange(tl)?.let { (a, b) ->
            val tabMs = origTabMs() ?: 0.0
            if (tabMs < tl.entries[a].startMs || tabMs >= tl.entries[b].endMs) {
                seekSerial++
                origSeekTab(tl.entries[a].startMs)
            }
        }
        if (countIn) {
            scope.launch {
                countingIn = true
                val pos = tl.locate(origTabMs() ?: 0.0)
                val m = tl.track.measures[pos?.measure ?: 0]
                val barBeats = m.timeSignature[0].coerceAtLeast(1)
                val beats = (countInBeats ?: barBeats).coerceIn(1, 32)
                val beatMs = 60_000.0 / (m.bpm * speed) * 4.0 / m.timeSignature[1].coerceAtLeast(1)
                repeat(beats) { k ->
                    click(k % barBeats == 0)
                    delay(beatMs.toLong())
                }
                countingIn = false
                origPlay()
            }
        } else {
            origPlay()
        }
    }

    /** Tap on a bar: set loop ends while choosing a loop, otherwise jump there. */
    fun onMeasureTapped(measure: Int) {
        val tl = ready?.timeline ?: return
        if (loopSelecting) {
            val from = loopFrom
            if (from == null) {
                loopFrom = measure
            } else {
                setLoop(from, measure, commit = true)
            }
            return
        }
        val entry = tl.entryOfMeasureNear(measure, cursor?.entry ?: 0).takeIf { it >= 0 } ?: return
        seekToEntry(entry)
    }

    private fun seekToEntry(entry: Int) {
        val tl = ready?.timeline ?: return
        seekSerial++
        if (sound == TabSound.SYNTH) {
            synthStartEntry = entry
            cursor = TabTimeline.Position(entry, tl.entries[entry].measure, 0, 0f)
            if (mediaPlayer?.isPlaying == true) playSynth(entry, withCountIn = false)
        } else {
            origSeekTab(tl.entries[entry].startMs)
        }
    }

    /** Passes already played of the tab's own repeat closing at bar [closing], if [entry] is inside it. */
    fun tabRepeatPass(entry: Int, closing: Int): Int? = ready?.timeline?.repeatPass(entry, closing)

    /** Loop button: start choosing a loop, or clear the current one. */
    fun toggleLoop() {
        if (loopActive || loopSelecting) {
            clearLoop()
            restartSynthIfPlaying()
        } else {
            loopSelecting = true
        }
    }

    /**
     * Sets the loop to bars [a]..[b] (either order). While dragging call with commit = false to
     * just show it; commit = true starts looping (jumps into the loop if outside it).
     */
    fun setLoop(a: Int, b: Int, commit: Boolean) {
        loopFrom = minOf(a, b)
        loopTo = maxOf(a, b)
        if (!commit) return
        loopSelecting = false
        val tl = ready?.timeline ?: return
        val range = loopRange(tl) ?: return
        val cur = cursor?.entry
        val inside = cur != null && cur in range.first..range.second
        if (!inside) seekSerial++
        if (sound == TabSound.SYNTH) {
            val start = if (inside) cur!! else range.first
            synthStartEntry = start
            if (mediaPlayer?.isPlaying == true) playSynth(start, withCountIn = false)
            else cursor = TabTimeline.Position(start, tl.entries[start].measure, 0, 0f)
        } else {
            if (!inside) origSeekTab(tl.entries[range.first].startMs)
            startLooper()
        }
    }

    fun clearLoop() {
        loopSelecting = false
        loopFrom = null
        loopTo = null
        stopLooper(handBack = true)
    }

    private fun loopRange(tl: TabTimeline): Pair<Int, Int>? {
        val from = loopFrom ?: return null
        val to = loopTo ?: return null
        val a = tl.entryOfMeasureNear(from, cursor?.entry ?: 0).takeIf { it >= 0 } ?: return null
        var b = a
        for (i in a until tl.entries.size) {
            if (tl.entries[i].measure == to) { b = i; break }
            if (i > a && tl.entries[i].measure < from) break
            b = i
        }
        return a to b
    }

    // ── Synth ──

    private fun playSynth(fromEntry: Int, withCountIn: Boolean) {
        val tl = ready?.timeline ?: return
        stopSynth()
        val range = loopRange(tl)
        val from = fromEntry.coerceIn(0, (tl.entries.size - 1).coerceAtLeast(0))
        val sequence: List<Int> = if (range != null && from in range.first..range.second) {
            // The loop is written out many times in one file, so it repeats with no gap.
            val once = (range.first..range.second).toList()
            val passMs = once.sumOf { tl.entries[it].durationMs } / speed
            val reps = (LOOP_FILE_MS / passMs.coerceAtLeast(1.0)).toInt().coerceIn(2, 64)
            (from..range.second).toList() + List(reps) { once }.flatten()
        } else {
            (from until tl.entries.size).toList()
        }
        val partList = parts.values.toList().ifEmpty { listOf(TabMidi.Part(tl.track)) }
        fun buildFile(real: Boolean) = TabMidi.build(
            tl, sequence,
            TabMidi.Options(
                speed = speed, pitchShift = pitch, metronome = metronome, countIn = withCountIn,
                countInBeats = countInBeats, realStrings = real,
            ),
            partList,
        )
        var result = buildFile(realStrings)
        // Guitar / bass on modelled strings; if the audio track can't open, fall back to MIDI.
        val score = result.strings
        val strings = if (score != null) StringSynthPlayer.create(score) else null
        if (score != null && strings == null) result = buildFile(false)
        val file = File(appContext.cacheDir, "tab_synth.mid")
        runCatching {
            file.writeBytes(result.bytes)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { onSynthComplete() }
            mp.prepare()
            strings?.prime()
            mp.start()
            strings?.start()
            stringPlayer = strings
            mediaPlayer = mp
            synthResult = result
            synthStartEntry = from
            clock.reset()
            isPlaying = true
        }.onFailure {
            Log.w(TAG, "Synth failed: ${it.message}")
            strings?.release()
            stopSynth()
        }
    }

    private fun onSynthComplete() {
        val range = ready?.timeline?.let { loopRange(it) }
        if (range != null) {
            seekSerial++
            playSynth(range.first, withCountIn = false)
        } else {
            stopSynth()
            synthStartEntry = 0
        }
    }

    private fun restartSynthIfPlaying() {
        if (sound != TabSound.SYNTH || mediaPlayer?.isPlaying != true) return
        seekSerial++
        playSynth(cursor?.entry ?: synthStartEntry, withCountIn = false)
    }

    private fun stopSynth() {
        stringPlayer?.release()
        stringPlayer = null
        mediaPlayer?.let { mp ->
            runCatching { mp.stop() }
            mp.release()
        }
        mediaPlayer = null
        synthResult = null
        clock.reset()
        if (sound == TabSound.SYNTH) isPlaying = false
    }

    // ── Clicks (count-in and metronome over the original) ──

    private fun prepareClicks() {
        if (soundPool != null) return
        runCatching {
            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()
            val dir = File(appContext.cacheDir, "songsterr").apply { mkdirs() }
            val a = File(dir, "click_accent.wav").apply { writeBytes(clickWav(1760.0)) }
            val n = File(dir, "click.wav").apply { writeBytes(clickWav(1320.0)) }
            clickAccent = pool.load(a.absolutePath, 1)
            clickNormal = pool.load(n.absolutePath, 1)
            soundPool = pool
        }
    }

    private fun click(accent: Boolean) {
        soundPool?.play(if (accent) clickAccent else clickNormal, 1f, 1f, 1, 0, 1f)
    }

    private fun clickWav(freq: Double): ByteArray {
        val rate = 44_100
        val n = rate * 35 / 1000
        val pcm = ByteArrayOutputStream()
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val v = (sin(2 * PI * freq * t) * exp(-t * 90) * 0.9 * Short.MAX_VALUE).toInt()
            pcm.write(v and 0xFF)
            pcm.write((v shr 8) and 0xFF)
        }
        val data = pcm.toByteArray()
        val out = ByteArrayOutputStream()
        fun int(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
        out.write("data".toByteArray()); int(data.size); out.write(data)
        return out.toByteArray()
    }

    // ── Per-frame update ──

    /** Call every frame while the Instruments page is on screen. */
    fun tick() {
        val now = System.nanoTime()
        drums.tickCalibration(now)
        val tl = ready?.timeline ?: return
        if (seekSerial != lastSeekSeen) {
            lastSeekSeen = seekSerial
            drums.onSeek()
        }
        val range = loopRange(tl)
        if (sound == TabSound.SYNTH) {
            val mp = mediaPlayer
            val res = synthResult
            if (mp == null || res == null) {
                playStateChanged(false)
                updateSounding(false)
                return
            }
            val raw = runCatching { mp.currentPosition.toDouble() }.getOrDefault(0.0)
            val playing = runCatching { mp.isPlaying }.getOrDefault(false)
            playStateChanged(playing)
            // Keep the modelled strings on the MIDI player's clock.
            stringPlayer?.let { sp ->
                if (playing) StringSynthPlayer.midiNowMs(mp, now)?.let { (midiMs, precise) -> sp.follow(midiMs, precise, now) }
            }
            // The file already has the speed baked in, so it runs at real time.
            val ms = clock.update(raw, playing, 1.0, now).coerceAtMost(res.totalMs)
            val (entry, f) = res.locate(ms) ?: return
            val e = tl.entries.getOrNull(entry) ?: return
            val len = tl.track.measures[e.measure].lengthTicks
            cursor = TabTimeline.Position(entry, e.measure, (f * len).toLong().coerceAtMost(len - 1), f)
            lookahead = res.locate(ms + beatMs(tl, e.measure) / speed)?.let { (le, lf) ->
                tl.entries.getOrNull(le)?.let { x ->
                    val l = tl.track.measures[x.measure].lengthTicks
                    TabTimeline.Position(le, x.measure, (lf * l).toLong().coerceAtMost(l - 1), lf)
                }
            }
            if (range != null && entry == range.first &&
                (lastTickEntry == range.second && (range.first != range.second || f < lastTickF - 0.5f))
            ) drums.onLoopPass()
            lastTickEntry = entry
            lastTickF = f
            updateSounding(playing)
            drumsFrame(tl, now, playing, synth = true, entry, f * e.durationMs, range, canSuggest = false)
            return
        }
        val playing = origIsPlaying()
        isPlaying = playing || countingIn
        playStateChanged(playing)
        val raw = origTabMs() ?: return
        val loopA = range?.let { tl.entries[it.first].startMs } ?: Double.NaN
        val loopB = range?.let { tl.entries[it.second].endMs } ?: Double.NaN
        val tabMs = clock.update(raw, playing, speed.toDouble(), now, loopA, loopB)
        if (clock.wraps != lastWraps) {
            if (clock.wraps > lastWraps) drums.onLoopPass()
            lastWraps = clock.wraps
        }
        val pos = tl.locate(tabMs)
        cursor = pos
        updateSounding(playing)
        lookahead = pos?.let {
            var ahead = tabMs + beatMs(tl, it.measure)
            if (range != null && ahead >= loopB) ahead = loopA + (ahead - loopB)
            tl.locate(ahead)
        }
        if (fadeStartAt != 0L) {
            // Fade back in after a seek-based loop wrap.
            val fade = ((SystemClock.uptimeMillis() - fadeStartAt) / 90f).coerceIn(0f, 1f)
            runCatching { controller?.volume = fade }
            if (fade >= 1f) fadeStartAt = 0L
        }
        if (pos != null) {
            drumsFrame(tl, now, playing, synth = false, pos.entry, tabMs - tl.entries[pos.entry].startMs, range, canSuggest = backing == null)
        }
        if (!playing || pos == null) {
            lastBeatKey = -1
            return
        }
        if (looper == null && range != null) {
            val (a, b) = range
            // No gapless loop player for this audio: jump back just before the end, fading in.
            // (The smooth clock treats the jump as the loop carrying on, so the cursor doesn't snap.)
            if (raw >= tl.entries[b].endMs - 25 || raw < tl.entries[a].startMs - 1500) {
                runCatching { controller?.volume = 0f }
                fadeStartAt = SystemClock.uptimeMillis()
                origSeekTab(tl.entries[a].startMs)
                lastBeatKey = -1
                return
            }
        }
        if (metronome) {
            val m = tl.track.measures[pos.measure]
            val beatTicks = TabParser.TICKS_PER_WHOLE / m.timeSignature[1].coerceAtLeast(1)
            val beat = floor(pos.tick.toDouble() / beatTicks).toLong()
            val key = pos.entry * 64L + beat
            if (key != lastBeatKey) {
                if (lastBeatKey != -1L) click(beat == 0L)
                lastBeatKey = key
            }
        }
    }

    /**
     * Which parts have a note sounding at the cursor (every part shares the bar grid). Muted
     * parts and a paused player show none. Let-ring notes count to the end of their bar.
     */
    private fun updateSounding(playing: Boolean) {
        val pos = cursor
        val next: Set<Int> = if (!playing || pos == null) emptySet() else buildSet {
            for ((index, part) in parts) {
                if (index in muted) continue
                val m = part.track.measures.getOrNull(pos.measure) ?: continue
                val t = pos.tick
                val on = m.beats.any { b ->
                    !b.isRest && b.onsetTicks <= t &&
                        (t < b.onsetTicks + b.durationTicks || b.letRing)
                }
                if (on) add(index)
            }
        }
        if (next != soundingParts) soundingParts = next
    }

    /** The bar entry played after [entry] (the loop's first bar at the loop's end), or null. */
    fun nextEntry(entry: Int): Int? {
        val tl = ready?.timeline ?: return null
        val range = loopRange(tl)
        return if (range != null && entry == range.second) range.first else (entry + 1).takeIf { it < tl.entries.size }
    }

    /** One beat of bar [measure], in tab ms. */
    private fun beatMs(tl: TabTimeline, measure: Int): Double {
        val m = tl.track.measures[measure]
        return 60_000.0 / m.bpm.coerceAtLeast(1.0) * 4.0 / m.timeSignature.getOrElse(1) { 4 }.coerceAtLeast(1)
    }

    private fun playStateChanged(playing: Boolean) {
        if (playing == wasPlayingTick) return
        wasPlayingTick = playing
        if (playing) drums.onPlay() else drums.onStop()
    }

    private fun drumsFrame(
        tl: TabTimeline, now: Long, playing: Boolean, synth: Boolean,
        entry: Int, posTab: Double, range: Pair<Int, Int>?, canSuggest: Boolean,
    ) {
        val e = tl.entries[entry]
        val nextEntry = if (range != null && entry == range.second) range.first else (entry + 1).takeIf { it < tl.entries.size }
        val ne = nextEntry?.let { tl.entries[it] }
        drums.frame(
            now, playing, synth, speed.toDouble(),
            entry, e.measure, posTab, e.durationMs, tl.track.measures[e.measure].lengthTicks,
            nextEntry, ne?.measure ?: 0, ne?.durationMs ?: 0.0, ne?.let { tl.track.measures[it.measure].lengthTicks } ?: 1L,
            canSuggest,
        )
    }

    companion object {
        private const val TAG = "TabPractice"
        /** How much of a loop is written into one synth file (repeats until then are gapless). */
        private const val LOOP_FILE_MS = 8 * 60_000.0
    }
}
