package com.theveloper.pixelplay.presentation.components.tabs

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a player's position (which only updates every few tens of ms, and in steps) into a
 * smooth clock: it runs on the frame clock at the playback rate and eases toward the player's
 * reported position.
 *
 * Loop-aware: while a loop [loopStartMs]..[loopEndMs] is set, the player jumping from the end
 * back to the start is treated as the music carrying on (the clock keeps running on an
 * "unwrapped" timeline and folds the result back into the loop), so a loop wrap never snaps or
 * stutters. Only real jumps (seeks) snap.
 */
internal class SmoothClock {
    private var anchorMs = 0.0
    private var anchorAt = 0L
    private var lastRaw = Double.NaN
    private var lastOut = 0.0
    private var running = false
    /** Loop length added for every wrap seen (raw + wrapAdd = unwrapped). */
    private var wrapAdd = 0.0
    private var loopA = Double.NaN
    private var loopB = Double.NaN

    /** Wraps seen since the clock started (each one = one more pass of the loop). */
    var wraps = 0
        private set

    /** Unwrapped position from the last update (keeps growing across loop passes). */
    var unwrappedMs = 0.0
        private set

    fun reset() {
        running = false
        lastRaw = Double.NaN
        wrapAdd = 0.0
        wraps = 0
    }

    fun update(
        raw: Double,
        playing: Boolean,
        rate: Double,
        nowNanos: Long,
        loopStartMs: Double = Double.NaN,
        loopEndMs: Double = Double.NaN,
    ): Double {
        // Loop changed (set, moved or cleared): rebase onto the plain timeline.
        if (!(loopStartMs == loopA || (loopStartMs.isNaN() && loopA.isNaN())) ||
            !(loopEndMs == loopB || (loopEndMs.isNaN() && loopB.isNaN()))
        ) {
            anchorMs -= wrapAdd
            lastOut -= wrapAdd
            wrapAdd = 0.0
            loopA = loopStartMs
            loopB = loopEndMs
        }
        val loopLen = if (!loopA.isNaN() && !loopB.isNaN() && loopB - loopA > 50) loopB - loopA else 0.0

        if (!playing) {
            running = false
            lastRaw = raw
            wrapAdd = 0.0
            lastOut = raw
            unwrappedMs = raw
            return raw
        }
        if (!running) {
            running = true
            wrapAdd = 0.0
            anchorMs = raw
            anchorAt = nowNanos
            lastRaw = raw
            lastOut = raw
            unwrappedMs = raw
            return raw
        }
        // A jump back to the loop start from near its end is the loop carrying on.
        if (loopLen > 0 && raw != lastRaw && !lastRaw.isNaN() &&
            lastRaw - raw > loopLen * 0.5 && raw < loopA + loopLen * 0.5
        ) {
            wrapAdd += loopLen
            wraps++
        }
        val rawU = raw + wrapAdd
        val predicted = anchorMs + (nowNanos - anchorAt) / 1e6 * rate
        var out = predicted
        if (raw != lastRaw) {
            lastRaw = raw
            val err = rawU - predicted
            if (abs(err) > SNAP_MS * rate.coerceAtLeast(0.5)) {
                anchorMs = rawU
                anchorAt = nowNanos
                lastOut = rawU
                unwrappedMs = rawU
                return fold(rawU, loopLen)
            }
            // Ease a little of the error in each time the player reports.
            anchorMs = predicted + err * 0.15
            anchorAt = nowNanos
            out = anchorMs
        }
        // Never step backwards by a few ms (that reads as jitter).
        if (out < lastOut && lastOut - out < SNAP_MS) out = lastOut
        lastOut = out
        unwrappedMs = out
        return fold(out, loopLen)
    }

    private fun fold(u: Double, loopLen: Double): Double {
        if (loopLen <= 0 || u < loopB) return u
        return loopA + ((u - loopA) % loopLen)
    }

    private companion object {
        const val SNAP_MS = 220.0
    }
}

/**
 * The on-screen cursor's motion between lines (and around loop wraps).
 *
 * Inside a line the cursor simply follows the music. When it moves to another line (the next
 * line, or back to a loop's start) the old cursor carries on sliding right at its speed and
 * fades out, while the new one slides in from the left and fades in. A seek snaps.
 */
internal class CursorGlide(private val dp: Float) {
    class Mark(val system: Int, val x: Float, val alpha: Float)

    private var system = -1
    private var x = 0f
    private var lastAt = 0L
    private var velocity = 0f // px per ms
    private var seekSerial = -1

    private var ghostSystem = -1
    private var ghostX = 0f
    private var ghostV = 0f
    private var transitionAt = 0L
    private var enterFrom = 0f

    fun reset() {
        system = -1
        ghostSystem = -1
        velocity = 0f
    }

    /**
     * [target] = the line and x the music is at now (null = no cursor). [serial] changes on every
     * seek (those snap). Returns the cursor marks to draw this frame.
     */
    fun update(targetSystem: Int?, targetX: Float, nowMs: Long, serial: Int, playing: Boolean): List<Mark> {
        if (targetSystem == null) {
            reset()
            return emptyList()
        }
        val seek = serial != seekSerial
        seekSerial = serial
        val dt = (nowMs - lastAt).coerceIn(1L, 100L).toFloat()
        val jumpedBack = targetSystem == system && targetX < x - 40 * dp
        if (system < 0 || seek || !playing) {
            ghostSystem = -1
            velocity = 0f
        } else if (targetSystem != system || jumpedBack) {
            // Line change or loop wrap: the old cursor slides on and fades, the new one slides in.
            ghostSystem = system
            ghostX = x
            ghostV = velocity.coerceIn(0.02f * dp, 0.6f * dp)
            transitionAt = nowMs
            enterFrom = ENTER_DP * dp
            velocity = 0f
        } else {
            val v = (targetX - x) / dt
            if (v >= 0) velocity = velocity * 0.8f + v * 0.2f
        }
        system = targetSystem
        x = targetX
        lastAt = nowMs

        val marks = ArrayList<Mark>(2)
        val since = (nowMs - transitionAt).toFloat()
        if (ghostSystem >= 0) {
            val t = since / GHOST_MS
            if (t >= 1f) {
                ghostSystem = -1
            } else {
                marks += Mark(ghostSystem, ghostX + ghostV * since, 1f - easeOut(t))
            }
        }
        val inT = if (transitionAt == 0L) 1f else min(1f, since / ENTER_MS)
        val e = easeOut(inT)
        marks += if (inT < 1f) Mark(system, x - enterFrom * (1f - e), max(0.15f, e)) else Mark(system, x, 1f)
        return marks
    }

    private fun easeOut(t: Float): Float {
        val u = 1f - t.coerceIn(0f, 1f)
        return 1f - u * u * u
    }

    companion object {
        const val GHOST_MS = 200f
        const val ENTER_MS = 170f
        const val ENTER_DP = 26f
    }
}

/**
 * Where the cursor is inside a bar: a smooth (monotone cubic) sweep through the notes, so it
 * lands on every note exactly when it plays but doesn't change speed with a jolt at each one.
 * [endX] is where the bar's last tick should arrive (the next bar's first note when that bar is
 * next on the same line), so the cursor crosses the barline instead of jumping over it.
 */
internal fun PlacedMeasure.sweepX(tick: Double, real: Int?, endX: Float?): Float {
    if (multiRest > 1 || slots.isEmpty()) return xAt(tick.toLong(), real)
    val len = measure.lengthTicks.coerceAtLeast(1).toDouble()
    val ts = ArrayList<Double>(slots.size + 2)
    val xs = ArrayList<Double>(slots.size + 2)
    if (slots.first().slot.onsetTicks > 0) {
        ts += 0.0
        xs += (x + (slots.first().x - x) * 0.4f).toDouble()
    }
    for (s in slots) {
        val t = s.slot.onsetTicks.toDouble()
        if (ts.isNotEmpty() && t <= ts.last()) continue
        ts += t
        xs += s.x.toDouble()
    }
    ts += len
    xs += max((endX ?: contentRight), xs.last().toFloat()).toDouble()
    return monotoneCubic(ts, xs, tick.coerceIn(0.0, len)).toFloat()
}

/** Fritsch–Carlson monotone cubic interpolation (no overshoot, continuous speed). */
internal fun monotoneCubic(t: List<Double>, y: List<Double>, at: Double): Double {
    val n = t.size
    if (n == 0) return 0.0
    if (n == 1 || at <= t[0]) return y[0]
    if (at >= t[n - 1]) return y[n - 1]
    val d = DoubleArray(n - 1) { (y[it + 1] - y[it]) / (t[it + 1] - t[it]).coerceAtLeast(1e-9) }
    val m = DoubleArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
    for (i in 0 until n - 1) {
        if (d[i] == 0.0) {
            m[i] = 0.0; m[i + 1] = 0.0
            continue
        }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9) {
            val tau = 3 / kotlin.math.sqrt(s)
            m[i] = tau * a * d[i]
            m[i + 1] = tau * b * d[i]
        }
    }
    var k = 0
    while (k < n - 2 && at > t[k + 1]) k++
    val h = t[k + 1] - t[k]
    val s = (at - t[k]) / h
    val s2 = s * s
    val s3 = s2 * s
    return (2 * s3 - 3 * s2 + 1) * y[k] + (s3 - 2 * s2 + s) * h * m[k] + (-2 * s3 + 3 * s2) * y[k + 1] + (s3 - s2) * h * m[k + 1]
}
