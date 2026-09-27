package com.theveloper.pixelplay.presentation.components.tabs

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.theveloper.pixelplay.data.drumkit.AudioRoute
import com.theveloper.pixelplay.data.drumkit.DrumCalibrator
import com.theveloper.pixelplay.data.drumkit.DrumHit
import com.theveloper.pixelplay.data.drumkit.DrumJudge
import com.theveloper.pixelplay.data.drumkit.DrumKitConnection
import com.theveloper.pixelplay.data.drumkit.DrumMap
import com.theveloper.pixelplay.data.drumkit.DrumPiece
import com.theveloper.pixelplay.data.drumkit.DrumScore
import com.theveloper.pixelplay.data.drumkit.DrumStats
import com.theveloper.pixelplay.data.drumkit.KitProfiles
import com.theveloper.pixelplay.data.songsterr.RenderedTrack
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Playing along on an electronic drum kit: the kit connection, the pad map, scoring against the
 * drum tab, the tap-test latency, the tab-timing suggestion and the summary cards.
 * Owned by [TabPracticeController]; everything here runs on the main thread.
 */
@Stable
class DrumSession(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("drum_kit", Context.MODE_PRIVATE)

    // ── Connection ──
    var status by mutableStateOf<DrumKitConnection.Status>(DrumKitConnection.Status.NoKit)
        private set
    val kitName: String? get() = (status as? DrumKitConnection.Status.Connected)?.name
    val connected: Boolean get() = status is DrumKitConnection.Status.Connected

    private val connection = DrumKitConnection(appContext, ::onKitHit) { s -> onStatus(s) }

    // ── Settings ──
    /** Score hits automatically while a kit is connected and a drum part is on screen. */
    var scoringEnabled by mutableStateOf(prefs.getBoolean("scoring", true))
        private set
    var map by mutableStateOf(DrumMap(KitProfiles.GM))
        private set
    var route by mutableStateOf(AudioRoute.SPEAKER)
        private set
    /** Latency allowed for (ms) on the current output, for ORIG. and SYNTH. */
    var latencyOrig by mutableIntStateOf(DEFAULT_ORIG)
        private set
    var latencySynth by mutableIntStateOf(DEFAULT_SYNTH)
        private set
    var calibratedRoute by mutableStateOf(false)
        private set

    // ── Live ──
    val judge = DrumJudge(map)
    /** Bumped when colours / marks change, so the tab redraws. */
    var version by mutableIntStateOf(0)
        private set
    /** The part on screen is drums (scoring needs a drum part). */
    var hasScore by mutableStateOf(false)
        private set
    /** Last note the kit sent (for "hit a pad" in the mapping editor), with a counter so repeats show. */
    var lastNote by mutableStateOf<Int?>(null)
        private set
    var lastNoteSerial by mutableIntStateOf(0)
        private set
    /** A note learned automatically, shown briefly ("Pad 31 → Snare"). */
    var learnedToast by mutableStateOf<Pair<Int, DrumPiece>?>(null)
    /** "Tab seems N ms early/late": the change to make to the tab timing (ms), or null. */
    var suggestionMs by mutableStateOf<Int?>(null)
        private set
    private var dismissedMs: Int? = null
    /** Summary card to show (after a run or a loop pass), or null. */
    var summary by mutableStateOf<Summary?>(null)
    var liveAccuracy by mutableStateOf<Float?>(null)
        private set

    class Summary(val stats: DrumStats, val loopPass: Boolean, val at: Long = SystemClock.uptimeMillis())

    // ── Calibration ──
    private val calibrator = DrumCalibrator(appContext)
    var calibrating by mutableStateOf<DrumCalibrator.Mode?>(null)
        private set
    var calibrationProgress by mutableIntStateOf(0)
        private set
    var calibrationResult by mutableStateOf<DrumCalibrator.Result?>(null)
        private set

    private var active = false
    private var lastSuggestCheck = 0L

    val isScoring: Boolean get() = connected && hasScore && scoringEnabled && calibrating == null

    fun start() {
        refreshRoute()
        connection.start()
    }

    fun stop() {
        calibrator.stop()
        calibrating = null
        connection.stop()
    }

    private fun onStatus(s: DrumKitConnection.Status) {
        status = s
        if (s is DrumKitConnection.Status.Connected) {
            val profile = KitProfiles.forDeviceName(s.name)
            map = DrumMap.decode(prefs.getString(mapKey(s.name), null), profile)
            judge.map = map
            refreshRoute()
        }
    }

    private fun mapKey(name: String) = "map_" + name.lowercase().filter { it.isLetterOrDigit() }

    private fun saveMap() {
        val name = kitName ?: return
        prefs.edit().putString(mapKey(name), map.encode()).apply()
    }

    fun refreshRoute() {
        route = AudioRoute.current(appContext)
        latencyOrig = prefs.getInt("lat_${route.name}_ORIG", DEFAULT_ORIG)
        latencySynth = prefs.getInt("lat_${route.name}_SYNTH", DEFAULT_SYNTH)
        calibratedRoute = prefs.contains("lat_${route.name}_ORIG") || prefs.contains("lat_${route.name}_SYNTH")
    }

    fun setLatency(synth: Boolean, ms: Int) {
        val v = ms.coerceIn(-100, 400)
        if (synth) latencySynth = v else latencyOrig = v
        prefs.edit().putInt("lat_${route.name}_${if (synth) "SYNTH" else "ORIG"}", v).apply()
        calibratedRoute = true
    }

    fun toggleScoring() {
        scoringEnabled = !scoringEnabled
        prefs.edit().putBoolean("scoring", scoringEnabled).apply()
        if (!scoringEnabled) {
            judge.clearMarks()
            bump()
        }
    }

    // ── Mapping editor ──

    fun assign(note: Int, piece: DrumPiece?) {
        map = map.withManual(note, piece)
        judge.map = map
        saveMap()
    }

    fun resetMap() {
        map = map.reset()
        judge.map = map
        judge.learner.clear()
        saveMap()
    }

    // ── Score ──

    fun setScore(track: RenderedTrack?) {
        hasScore = track?.isDrums == true
        judge.setScore(if (hasScore && track != null) DrumScore.expected(track) else emptyMap())
        suggestionMs = null
        dismissedMs = null
        judge.estimator.clear()
        bump()
    }

    // ── Playback events (from the controller) ──

    fun onPlay() {
        if (!isScoring) return
        judge.startRun()
        judge.clearMarks()
        liveAccuracy = null
        bump()
    }

    fun onStop() {
        val run = judge.run
        if (isScoring && run.judged >= 8) summary = Summary(run, loopPass = false)
    }

    fun onSeek() {
        judge.onSeek(System.nanoTime() / 1e6)
        bump()
    }

    fun onLoopPass() {
        if (!isScoring) return
        val seg = judge.takeSegment()
        if (seg.judged >= 4) summary = Summary(seg, loopPass = true)
    }

    /**
     * Every frame while the Instruments page is showing. [posTab] is tab ms into bar [entry];
     * [canSuggest] = the tab timing can be moved (the song's own audio is playing).
     */
    fun frame(
        nowNanos: Long,
        playing: Boolean,
        synth: Boolean,
        speed: Double,
        entry: Int, measure: Int, posTab: Double, durTab: Double, lengthTicks: Long,
        nextEntry: Int?, nextMeasure: Int, nextDurTab: Double, nextLengthTicks: Long,
        canSuggest: Boolean,
    ) {
        noteMode(synth)
        val on = isScoring && playing
        if (!on) {
            active = false
            return
        }
        active = true
        val latency = (if (synth) latencySynth else latencyOrig).toDouble()
        val before = judge.version
        judge.frame(
            nowNanos / 1e6, latency, true, speed,
            entry, measure, posTab, durTab, lengthTicks,
            nextEntry, nextMeasure, nextDurTab, nextLengthTicks,
        )
        if (judge.version != before) bump()
        val now = SystemClock.uptimeMillis()
        if (now - lastSuggestCheck > 1000) {
            lastSuggestCheck = now
            liveAccuracy = judge.run.takeIf { it.judged >= 4 }?.accuracy
            if (canSuggest && suggestionMs == null) {
                judge.estimator.suggestion()?.let { s ->
                    val r = (s / 5).roundToInt() * 5
                    val d = dismissedMs
                    if (d == null || abs(r - d) > 40) suggestionMs = r
                }
            }
        }
    }

    /** The user accepted the suggestion: returns the ms to add to the tab timing. */
    fun acceptSuggestion(): Int? {
        val s = suggestionMs ?: return null
        suggestionMs = null
        dismissedMs = null
        judge.estimator.clear()
        return s
    }

    fun dismissSuggestion() {
        dismissedMs = suggestionMs
        suggestionMs = null
        judge.estimator.clear()
    }

    private fun onKitHit(hit: DrumHit) {
        lastNote = hit.note
        lastNoteSerial++
        if (calibrating != null) {
            calibrator.onHit(hit)
            return
        }
        if (!active) {
            // Still note which pads exist (for the editor).
            judge.seenNotes[hit.note] = hit.velocity
            return
        }
        val latency = (if (lastFrameSynth) latencySynth else latencyOrig).toDouble()
        val out = judge.onHit(hit, hit.timeNanos / 1e6, latency)
        out.learned?.let { (note, piece) ->
            map = judge.map
            saveMap()
            learnedToast = note to piece
        }
        bump()
    }

    private var lastFrameSynth = false

    /** Remember which mode the last frame was in (hits between frames use its latency). */
    fun noteMode(synth: Boolean) {
        lastFrameSynth = synth
    }

    // ── Calibration ──

    fun startCalibration(synth: Boolean) {
        refreshRoute()
        calibrationResult = null
        val mode = if (synth) DrumCalibrator.Mode.SYNTH else DrumCalibrator.Mode.AUDIO
        if (calibrator.start(mode)) calibrating = mode
    }

    fun cancelCalibration() {
        calibrator.stop()
        calibrating = null
    }

    /** Every frame (even with nothing loaded) so the tap test can run. */
    fun tickCalibration(nowNanos: Long) {
        val mode = calibrating ?: return
        val r = calibrator.frame(nowNanos)
        calibrationProgress = calibrator.progress
        if (r != null) {
            calibrating = null
            calibrationResult = r
            if (r.latencyMs >= -100) setLatency(mode == DrumCalibrator.Mode.SYNTH, r.latencyMs)
        }
    }

    private fun bump() {
        version++
    }

    companion object {
        /** USB MIDI in + a little human reaction; the app's player already reports what's heard. */
        const val DEFAULT_ORIG = 15
        /** Android's MIDI synth reports its position ahead of what's heard. */
        const val DEFAULT_SYNTH = 60
        val CALIBRATION_TOTAL = DrumCalibrator.TOTAL
        val CALIBRATION_COUNT_IN = DrumCalibrator.COUNT_IN
    }
}

/** The kit's colours for the tab renderer (latest pass of each bar). */
internal fun DrumSession.drumMarks(c: ScoreColors): DrumMarks = object : DrumMarks {
    private var lastPm: PlacedMeasure? = null
    private var lastPass: DrumJudge.Pass? = null

    private fun passFor(pm: PlacedMeasure): DrumJudge.Pass? {
        if (pm === lastPm) return lastPass
        lastPm = pm
        lastPass = pm.realMeasures.mapNotNull { judge.marksFor(it) }.maxByOrNull { it.serial }
        return lastPass
    }

    override fun colorOf(pm: PlacedMeasure, tick: Long, articulation: Int): Int? =
        when (passFor(pm)?.stateOf(tick, articulation)) {
            com.theveloper.pixelplay.data.drumkit.HitState.GOOD -> c.hitGood
            com.theveloper.pixelplay.data.drumkit.HitState.EARLY,
            com.theveloper.pixelplay.data.drumkit.HitState.LATE,
            com.theveloper.pixelplay.data.drumkit.HitState.DYNAMICS,
            com.theveloper.pixelplay.data.drumkit.HitState.OPENNESS -> c.hitAmber
            com.theveloper.pixelplay.data.drumkit.HitState.MISS -> c.hitMiss
            com.theveloper.pixelplay.data.drumkit.HitState.NOT_ON_KIT -> c.faint
            else -> null
        }

    override fun strays(pm: PlacedMeasure): List<DrumMarks.Stray> =
        passFor(pm)?.ghosts?.map { DrumMarks.Stray(it.tick, it.piece.staffPos, c.hitMiss) } ?: emptyList()
}
