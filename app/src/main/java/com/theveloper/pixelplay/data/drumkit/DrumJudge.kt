package com.theveloper.pixelplay.data.drumkit

import com.theveloper.pixelplay.data.songsterr.RenderedTrack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How a tab note was played. */
enum class HitState {
    PENDING,
    /** In time (green). */
    GOOD,
    /** Hit, but early / late (amber). */
    EARLY, LATE,
    /** In time, but a ghost note hit hard or an accent hit soft (amber). */
    DYNAMICS,
    /** In time on the hi-hat, but with the pedal open instead of closed or the other way (amber). */
    OPENNESS,
    /** Not played, or played on the wrong pad (red). */
    MISS,
    /** The kit has nothing to play it with (grey, not counted). */
    NOT_ON_KIT,
    /** Due just after a seek: not judged (drawn normally). */
    SKIPPED;

    val isHit: Boolean get() = this == GOOD || this == EARLY || this == LATE || this == DYNAMICS || this == OPENNESS
    val isAmber: Boolean get() = this == EARLY || this == LATE || this == DYNAMICS || this == OPENNESS
}

enum class NoteDynamic { GHOST, NORMAL, ACCENT }

/** A note the tab expects, [tick] ticks into its bar. [articulation] is Songsterr's drum id. */
class ExpectedNote(val tick: Long, val articulation: Int, val piece: DrumPiece, val dynamic: NoteDynamic)

object DrumScore {
    /** Every drum note of the part, by bar (ties, rests and flam grace notes are skipped). */
    fun expected(track: RenderedTrack): Map<Int, List<ExpectedNote>> = track.measures.associate { m ->
        m.index to m.slots.flatMap { slot ->
            slot.beats.filter { !it.isRest }.flatMap { b ->
                b.notes.filter { !it.isTie && !it.isDead }.map { n ->
                    ExpectedNote(
                        slot.onsetTicks,
                        n.fret,
                        DrumPiece.ofArticulation(n.fret),
                        when {
                            n.isGhost -> NoteDynamic.GHOST
                            n.accent > 0 -> NoteDynamic.ACCENT
                            else -> NoteDynamic.NORMAL
                        },
                    )
                }
            }
        }.distinctBy { it.tick to it.articulation }
    }
}

/** Running totals for a run or one pass of a loop. */
class DrumStats {
    var good = 0; private set
    var amber = 0; private set
    var early = 0; private set
    var late = 0; private set
    var missed = 0; private set
    var extra = 0; private set
    private var errSum = 0.0
    private var errCount = 0
    private val perPiece = HashMap<DrumPiece, IntArray>() // [hits, total]

    val judged: Int get() = good + amber + missed
    val accuracy: Float get() = if (judged == 0) 0f else (good + amber).toFloat() / judged
    /** Average timing, ms (+ = late). */
    val meanErrorMs: Double get() = if (errCount == 0) 0.0 else errSum / errCount
    val isEmpty: Boolean get() = judged == 0 && extra == 0

    /** The piece with the lowest hit rate (at least 4 notes), or null. */
    val worstPiece: Pair<DrumPiece, Float>?
        get() = perPiece.filter { it.value[1] >= 4 }
            .map { it.key to it.value[0].toFloat() / it.value[1] }
            .minByOrNull { it.second }
            ?.takeIf { it.second < 0.95f }

    internal fun add(piece: DrumPiece, state: HitState, errMs: Double?) {
        val c = perPiece.getOrPut(piece) { IntArray(2) }
        when {
            state == HitState.GOOD -> good++
            state.isAmber -> {
                amber++
                if (state == HitState.EARLY) early++
                if (state == HitState.LATE) late++
            }
            state == HitState.MISS -> missed++
            else -> return
        }
        c[1]++
        if (state.isHit) c[0]++
        if (errMs != null) {
            errSum += errMs
            errCount++
        }
    }

    internal fun addExtra() { extra++ }
}

/**
 * The tab's timing against the player's, from how consistently early or late the hits are.
 * When the player is steady (low spread) but every hit is off by about the same amount, the tab
 * (not the player) is off.
 */
class OffsetEstimator(private val size: Int = 24) {
    private val samples = ArrayDeque<Double>()

    fun add(errMs: Double) {
        samples.addLast(errMs)
        while (samples.size > size) samples.removeFirst()
    }

    fun clear() = samples.clear()

    val count: Int get() = samples.size

    /** Suggested change to the tab timing (ms, + = the tab is early / move it later), or null. */
    fun suggestion(minSamples: Int = 16, maxSpreadMs: Double = 20.0, minOffsetMs: Double = 25.0): Double? {
        if (samples.size < minSamples) return null
        val med = median(samples.toList())
        val mad = median(samples.map { abs(it - med) })
        if (mad > maxSpreadMs || abs(med) < minOffsetMs) return null
        return med
    }

    companion object {
        fun median(v: List<Double>): Double {
            if (v.isEmpty()) return 0.0
            val s = v.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }
    }
}

/**
 * Learns which pad is which from playing: every hit is compared with the tab notes due around
 * it. An unknown MIDI note that keeps landing on the same kind of tab note gets that piece; a
 * mapped note that never matches its piece but keeps matching another is re-mapped.
 */
class AutoLearner(private val window: Int = 16) {
    private val obs = HashMap<Int, ArrayDeque<Set<DrumPiece>>>()

    fun clear() = obs.clear()

    /** Returns a new piece for [note] when there's enough evidence, else null. */
    fun observe(note: Int, near: Set<DrumPiece>, map: DrumMap): DrumPiece? {
        if (map.isPinned(note)) return null
        val q = obs.getOrPut(note) { ArrayDeque() }
        q.addLast(near)
        while (q.size > window) q.removeFirst()
        val nonEmpty = q.filter { it.isNotEmpty() }
        val current = map.pieceFor(note)
        if (current == null) {
            if (nonEmpty.size < 8) return null
            val shares = share(nonEmpty)
            val best = shares.maxByOrNull { it.value } ?: return null
            val second = shares.filter { it.key != best.key && it.key.group != best.key.group }.maxOfOrNull { it.value } ?: 0f
            if (best.value >= 0.75f && second < 0.5f) {
                obs.remove(note)
                return best.key
            }
            return null
        }
        if (nonEmpty.size < window * 3 / 4) return null
        val compatible = nonEmpty.count { s -> s.any { matchPiece(it, current) != PieceMatch.NONE } }.toFloat() / nonEmpty.size
        if (compatible >= 0.3f) return null
        val best = share(nonEmpty).maxByOrNull { it.value } ?: return null
        if (best.value >= 0.8f && best.key != current) {
            obs.remove(note)
            return best.key
        }
        return null
    }

    private fun share(list: List<Set<DrumPiece>>): Map<DrumPiece, Float> {
        val counts = HashMap<DrumPiece, Int>()
        list.forEach { s -> s.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.mapValues { it.value.toFloat() / list.size }
    }
}

/**
 * Scores kit hits against the tab as it plays.
 *
 * Time is "real" ms on the System.nanoTime() clock. The screen tells the judge each frame which
 * bar is playing and where in it ([frame]); the judge works out when each note of that bar (and
 * the next one, shortly before it starts) is heard, marks hits and misses, and keeps the colours
 * of each bar's latest pass.
 */
class DrumJudge(var map: DrumMap) {
    class Config(
        val goodMs: Double = 35.0,
        val okMs: Double = 80.0,
        /** Ignores a second hit on the same kind of drum this soon after one (flams, double triggers). */
        val flamMs: Double = 40.0,
        /** How far a hit is compared with the tab for the offset estimate. */
        val estimateMs: Double = 250.0,
        val learnMs: Double = 60.0,
        val graceMs: Double = 300.0,
    )

    var config = Config()

    class JNote(val exp: ExpectedNote, val offsetTab: Double) {
        var state = HitState.PENDING
            internal set
        var errMs = 0.0
            internal set
    }

    class Ghost(val tick: Long, val piece: DrumPiece)

    /** One time a bar is played. */
    class Pass internal constructor(
        val serial: Int,
        val entry: Int,
        val measure: Int,
        val durTab: Double,
        val lengthTicks: Long,
        val notes: List<JNote>,
    ) {
        internal var startReal = 0.0
        internal var speed = 1.0
        val ghosts = ArrayList<Ghost>()
        private val byKey = notes.associateBy { it.exp.tick to it.exp.articulation }
        fun noteReal(n: JNote) = startReal + n.offsetTab / speed
        fun stateOf(tick: Long, articulation: Int): HitState? = byKey[tick to articulation]?.state
    }

    /** What happened to one hit (for the debug view). */
    class Outcome(val piece: DrumPiece?, val state: HitState?, val errMs: Double?, val learned: Pair<Int, DrumPiece>?)

    private var expected: Map<Int, List<ExpectedNote>> = emptyMap()
    private var serial = 0
    private var current: Pass? = null
    private var next: Pass? = null
    private val live = ArrayList<Pass>()
    private val marks = HashMap<Int, Pass>()
    private val lastClaim = HashMap<DrumGroup, Double>()
    private var graceUntil = 0.0
    private var speed = 1.0

    var run = DrumStats()
        private set
    var segment = DrumStats()
        private set
    val estimator = OffsetEstimator()
    val learner = AutoLearner()
    /** Bumped whenever something visible changes (colours, marks). */
    var version = 0
        private set

    private val seenGroups = HashSet<DrumGroup>()
    private var totalHits = 0
    private val missNoGroup = HashMap<DrumPiece, Int>()
    private val sessionMissing = HashSet<DrumPiece>()
    /** MIDI notes the kit has sent (for the mapping editor). */
    val seenNotes = LinkedHashMap<Int, Int>() // note → last velocity
    /** A hit that lights up the mapping editor ("hit a pad to assign"). */
    var lastNote: Int? = null
        private set

    private val velocities = ArrayDeque<Int>()

    fun setScore(score: Map<Int, List<ExpectedNote>>) {
        expected = score
        clearPasses()
        marks.clear()
        version++
    }

    /** Starts new run totals (on play). */
    fun startRun() {
        run = DrumStats()
        segment = DrumStats()
    }

    private fun clearPasses() {
        live.clear()
        current = null
        next = null
    }

    /** A seek or jump: forget what's pending and ignore hits for a moment. */
    fun onSeek(nowReal: Double) {
        clearPasses()
        graceUntil = nowReal + config.graceMs
        lastClaim.clear()
        version++
    }

    private fun scale(): Double = 1.0 + max(0.0, 0.75 - speed) * 0.8
    private fun goodWin() = config.goodMs * scale()
    private fun okWin() = config.okMs * scale()

    private fun newPass(entry: Int, measure: Int, durTab: Double, lengthTicks: Long): Pass {
        val len = lengthTicks.coerceAtLeast(1)
        val notes = expected[measure].orEmpty().map { JNote(it, durTab * it.tick / len) }
        return Pass(++serial, entry, measure, durTab, len, notes).also { live += it }
    }

    /**
     * Called every frame while scoring. [posTab] = tab ms into bar [entry]; [nextEntry] etc. is
     * the bar that plays after it (the loop's first bar at a loop's end), or null at the end.
     */
    fun frame(
        nowReal: Double,
        latencyMs: Double,
        playing: Boolean,
        speed: Double,
        entry: Int,
        measure: Int,
        posTab: Double,
        durTab: Double,
        lengthTicks: Long,
        nextEntry: Int?,
        nextMeasure: Int,
        nextDurTab: Double,
        nextLengthTicks: Long,
    ) {
        if (!playing) return
        this.speed = speed.coerceAtLeast(0.05)
        var cur = current
        if (cur == null || cur.entry != entry) {
            val n = next
            cur = if (n != null && n.entry == entry) n else newPass(entry, measure, durTab, lengthTicks)
            if (n === cur) next = null
            current = cur
            marks[measure] = cur
            version++
        }
        cur.speed = this.speed
        cur.startReal = nowReal - posTab / this.speed
        val remaining = (durTab - posTab) / this.speed
        if (nextEntry != null && remaining < okWin() + 150) {
            var n = next
            if (n == null || n.entry != nextEntry) {
                n?.let { live.remove(it) }
                n = newPass(nextEntry, nextMeasure, nextDurTab, nextLengthTicks)
                next = n
            }
            n.speed = this.speed
            n.startReal = nowReal + remaining
        }
        sweep(nowReal - latencyMs)
    }

    private fun sweep(nowAdj: Double) {
        val it = live.iterator()
        var changed = false
        while (it.hasNext()) {
            val p = it.next()
            var pending = false
            for (n in p.notes) {
                if (n.state != HitState.PENDING) continue
                val t = p.noteReal(n)
                if (nowAdj <= t + okWin()) {
                    pending = true
                    continue
                }
                changed = true
                if (t < graceUntil) {
                    // Notes due during the grace period after a seek aren't judged (left uncoloured).
                    n.state = HitState.SKIPPED
                    continue
                }
                n.state = if (!map.canPlay(n.exp.piece) || n.exp.piece in sessionMissing) HitState.NOT_ON_KIT else HitState.MISS
                if (n.state == HitState.MISS) {
                    run.add(n.exp.piece, HitState.MISS, null)
                    segment.add(n.exp.piece, HitState.MISS, null)
                    noteMissing(n.exp.piece)
                }
            }
            if (!pending && p !== current && p !== next) it.remove()
        }
        if (changed) version++
    }

    /** A piece the kit never plays, missed twice while the kit is being played, goes grey. */
    private fun noteMissing(piece: DrumPiece) {
        if (piece.group in seenGroups || totalHits < 4) return
        // A kit profile that lists the piece means the kit has it: a miss is a miss.
        if (map.profile.pieces?.contains(piece) == true) return
        val c = (missNoGroup[piece] ?: 0) + 1
        missNoGroup[piece] = c
        if (c >= 2) sessionMissing += piece
    }

    /** Scores one hit. [hitReal] is when it was played (ms, System.nanoTime clock). */
    fun onHit(hit: DrumHit, hitReal: Double, latencyMs: Double): Outcome {
        seenNotes[hit.note] = hit.velocity
        lastNote = hit.note
        val t = hitReal - latencyMs
        // Learning: which tab notes are due right around this hit?
        val near = HashSet<DrumPiece>()
        for (p in live) for (n in p.notes) {
            if (n.state == HitState.PENDING && abs(p.noteReal(n) - t) <= config.learnMs) near += n.exp.piece
        }
        var learned: Pair<Int, DrumPiece>? = null
        if (t >= graceUntil) learner.observe(hit.note, near, map)?.let { piece ->
            map = map.withLearned(hit.note, piece)
            learned = hit.note to piece
        }
        val piece = resolvePiece(map, hit)
        version++
        if (piece == null || t < graceUntil) return Outcome(piece, null, null, learned)
        totalHits++
        if (seenGroups.add(piece.group)) {
            sessionMissing.removeAll { it.group == piece.group }
        }
        // Flams and double triggers: one hit per drum.
        lastClaim[piece.group]?.let { if (t - it in 0.0..config.flamMs) return Outcome(piece, null, null, learned) }

        var best: JNote? = null
        var bestPass: Pass? = null
        var bestScore = Double.MAX_VALUE
        var bestMatch = PieceMatch.NONE
        for (p in live) for (n in p.notes) {
            if (n.state != HitState.PENDING) continue
            val match = matchPiece(n.exp.piece, piece)
            if (match == PieceMatch.NONE) continue
            val err = t - p.noteReal(n)
            if (abs(err) > okWin()) continue
            val score = abs(err) + if (match == PieceMatch.HH_OPENNESS) 12.0 else 0.0
            if (score < bestScore) {
                bestScore = score
                best = n
                bestPass = p
                bestMatch = match
            }
        }
        if (best == null || bestPass == null) {
            // Not a tab note: a stray hit (or one too far off). Mark it, and use it for the
            // timing estimate if a note of that drum is near.
            estimateFrom(piece, t)
            val p = passAt(t)
            if (p != null) {
                val tick = ((t - p.startReal) * p.speed / p.durTab * p.lengthTicks).toLong().coerceIn(0, p.lengthTicks - 1)
                p.ghosts += Ghost(tick, piece)
            }
            run.addExtra()
            segment.addExtra()
            return Outcome(piece, null, null, learned)
        }
        val err = t - bestPass.noteReal(best)
        var state = when {
            abs(err) <= goodWin() -> HitState.GOOD
            err < 0 -> HitState.EARLY
            else -> HitState.LATE
        }
        if (state == HitState.GOOD && bestMatch == PieceMatch.HH_OPENNESS) state = HitState.OPENNESS
        if (state == HitState.GOOD && !dynamicsOk(best.exp.dynamic, hit.velocity)) state = HitState.DYNAMICS
        if (best.exp.dynamic == NoteDynamic.NORMAL) {
            velocities.addLast(hit.velocity)
            while (velocities.size > 32) velocities.removeFirst()
        }
        best.state = state
        best.errMs = err
        lastClaim[piece.group] = t
        run.add(best.exp.piece, state, err)
        segment.add(best.exp.piece, state, err)
        estimator.add(err * speed)
        return Outcome(piece, state, err, learned)
    }

    private fun estimateFrom(piece: DrumPiece, t: Double) {
        var bestErr: Double? = null
        for (p in live) for (n in p.notes) {
            if (matchPiece(n.exp.piece, piece) == PieceMatch.NONE) continue
            val err = t - p.noteReal(n)
            if (abs(err) <= config.estimateMs && (bestErr == null || abs(err) < abs(bestErr))) bestErr = err
        }
        bestErr?.let { estimator.add(it * speed) }
    }

    private fun passAt(t: Double): Pass? = live.firstOrNull { t >= it.startReal && t < it.startReal + it.durTab / it.speed }
        ?: current

    private fun medianVelocity(): Double =
        if (velocities.size < 8) 90.0 else OffsetEstimator.median(velocities.map { it.toDouble() })

    private fun dynamicsOk(d: NoteDynamic, velocity: Int): Boolean {
        val m = medianVelocity()
        return when (d) {
            NoteDynamic.GHOST -> velocity <= max(40.0, m * 0.75)
            NoteDynamic.ACCENT -> velocity >= min(118.0, m * 1.1)
            NoteDynamic.NORMAL -> true
        }
    }

    /** Colours of the latest pass of bar [measure] (null = never played / reset). */
    fun marksFor(measure: Int): Pass? = marks[measure]

    /** Returns the stats since the last call (one loop pass) and starts a new segment. */
    fun takeSegment(): DrumStats = segment.also { segment = DrumStats() }

    fun clearMarks() {
        marks.clear()
        version++
    }

    val missingPieces: Set<DrumPiece> get() = sessionMissing
}
