package com.theveloper.pixelplay.data.diagnostics

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Java-heap watchdog.
 *
 * `onTrimMemory` only reports *system* RAM pressure. It never fires when the app's own Java heap
 * (256 MB `growthLimit` on most phones) is nearly full, which is how every PixelPlayer OOM so far
 * happened: the device had plenty of RAM, the process hit its heap cap, and whichever thread
 * allocated next (the audio renderer, a JSON parser) crashed.
 *
 * This samples heap use every few seconds (two Runtime calls, no allocation) and, when it stays
 * high, asks registered caches to shrink before the heap runs out. Heavy optional work (audio
 * analysis, recommendations) can also check [isElevated] and skip a round.
 */
object HeapPressure {

    enum class Level { NORMAL, ELEVATED, CRITICAL }

    /** Heap used / heap limit at which caches are trimmed. */
    private const val ELEVATED_FRACTION = 0.72f

    /** At this point everything optional is dropped. */
    private const val CRITICAL_FRACTION = 0.86f

    private const val NORMAL_INTERVAL_MS = 5_000L
    private const val PRESSURED_INTERVAL_MS = 1_500L

    /** Don't re-run the same trim level more often than this. */
    private const val MIN_TRIM_GAP_MS = 20_000L

    private val trimmers = CopyOnWriteArrayList<Pair<String, (Level) -> Unit>>()
    private var job: Job? = null

    @Volatile
    var level: Level = Level.NORMAL
        private set

    private var lastTrimLevel: Level = Level.NORMAL
    private var lastTrimAtMs = 0L

    /** Registers [trimmer] (for app-lifetime objects only: the reference is kept forever). */
    fun register(name: String, trimmer: (Level) -> Unit) {
        trimmers += name to trimmer
    }

    fun usedFraction(): Float {
        val runtime = Runtime.getRuntime()
        val max = runtime.maxMemory()
        if (max <= 0L || max == Long.MAX_VALUE) return 0f
        val used = runtime.totalMemory() - runtime.freeMemory()
        return used.toFloat() / max
    }

    /** Free heap before the app's limit (not counting garbage the GC could still reclaim). */
    fun headroomBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    }

    fun isElevated(): Boolean = level != Level.NORMAL || usedFraction() >= ELEVATED_FRACTION

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            var consecutiveHigh = 0
            while (isActive) {
                val fraction = usedFraction()
                val sampled = when {
                    fraction >= CRITICAL_FRACTION -> Level.CRITICAL
                    fraction >= ELEVATED_FRACTION -> Level.ELEVATED
                    else -> Level.NORMAL
                }
                // "Used" includes garbage the concurrent GC hasn't collected yet, so one high
                // sample means little; two in a row (or one critical) means live data.
                consecutiveHigh = if (sampled == Level.NORMAL) 0 else consecutiveHigh + 1
                val effective = when {
                    sampled == Level.CRITICAL -> Level.CRITICAL
                    sampled == Level.ELEVATED && consecutiveHigh >= 2 -> Level.ELEVATED
                    else -> Level.NORMAL
                }
                level = effective
                if (effective != Level.NORMAL) maybeTrim(effective, fraction)
                delay(if (sampled == Level.NORMAL) NORMAL_INTERVAL_MS else PRESSURED_INTERVAL_MS)
            }
        }
    }

    /** Runs the trimmers now (e.g. from `onTrimMemory`, or after catching an OOM). */
    fun trimNow(requested: Level) {
        runTrimmers(requested, usedFraction())
    }

    private fun maybeTrim(requested: Level, fraction: Float) {
        val now = SystemClock.elapsedRealtime()
        val escalated = requested.ordinal > lastTrimLevel.ordinal
        if (!escalated && now - lastTrimAtMs < MIN_TRIM_GAP_MS) return
        runTrimmers(requested, fraction)
    }

    @Synchronized
    private fun runTrimmers(requested: Level, fraction: Float) {
        lastTrimLevel = requested
        lastTrimAtMs = SystemClock.elapsedRealtime()
        Timber.tag("HeapPressure").w(
            "Heap %.0f%% of %d MB: trimming caches (%s)",
            fraction * 100f, Runtime.getRuntime().maxMemory() / (1024 * 1024), requested
        )
        for ((name, trimmer) in trimmers) {
            try {
                trimmer(requested)
            } catch (t: Throwable) {
                if (t is VirtualMachineError && t !is OutOfMemoryError) throw t
                Timber.tag("HeapPressure").w(t, "Trimmer %s failed", name)
            }
        }
    }
}
