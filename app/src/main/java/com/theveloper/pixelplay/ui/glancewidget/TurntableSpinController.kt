package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.theveloper.pixelplay.data.preferences.TurntableOptions
import com.theveloper.pixelplay.data.preferences.WidgetSpinMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Drives the turntable's rotation.
 *
 * There is no animation primitive in `RemoteViews`, so "spinning" means re-rendering the disc
 * at a new angle and pushing it to the launcher. That is not free, so this controller exists
 * to make sure it only happens when it is actually buying something:
 *
 *  - only while audio is playing,
 *  - only while a turntable widget is actually placed,
 *  - only while the screen is on, and
 *  - never in battery saver.
 *
 * When any of those stops being true the loop is cancelled and the record parks at its
 * current angle, which is kept in memory so playback resumes from where it left off.
 */
object TurntableSpinController {

    /**
     * Degrees of rotation per published frame.
     *
     * The frame rate is derived from this and the chosen speed rather than fixed, because a
     * fixed rate wastes frames at the slow end: at 2 rpm an 8 fps loop publishes six frames
     * for every 9° of movement, five of which nobody can tell apart. Roughly 5° a frame is
     * the point where the motion still reads as continuous.
     */
    private const val DEGREES_PER_FRAME = 5f

    /** Clamps on the derived rate: never faster than 10 fps, never slower than 2 fps. */
    private const val MIN_FRAME_MS = 100L
    private const val MAX_FRAME_MS = 500L

    /** [WidgetSpinMode.TICK] advances in visible steps instead of a continuous turn. */
    private const val TICK_FRAME_MS = 2_000L

    /**
     * How long to wait before looking again while the screen is off or battery saver is on.
     * Polling at frame rate to discover that we should not be drawing is its own small waste.
     */
    private const val IDLE_POLL_MS = 2_000L

    /**
     * Frames between refreshes of the placed-widget list. `getGlanceIds` is a binder call, and
     * widgets are not added or removed mid-song often enough to justify one per frame.
     */
    private const val ID_REFRESH_FRAMES = 40

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var parkedAngleDeg: Float = 0f

    @Volatile
    private var spinStartedAtMs: Long = 0L

    @Volatile
    private var running: Boolean = false

    /**
     * The rate the loop was started with. Held separately from [TurntableOptions] so that a
     * caller who stops the loop without knowing the user's current speed still parks the
     * record at the right angle.
     */
    @Volatile
    private var activeDegreesPerMs: Float = 0f

    private var loop: Job? = null

    /**
     * The angle to draw right now, in degrees.
     *
     * [WidgetSpinMode.POSITION] derives it from the track position so it stays correct without
     * any extra updates at all; the other modes read the accumulator this controller advances.
     */
    fun currentAngle(options: TurntableOptions, positionMs: Long): Float {
        val angle = when (options.spinMode) {
            WidgetSpinMode.OFF -> 0f
            WidgetSpinMode.POSITION -> positionMs * options.degreesPerMs
            WidgetSpinMode.SMOOTH, WidgetSpinMode.TICK -> {
                if (running) {
                    val elapsed = SystemClock.elapsedRealtime() - spinStartedAtMs
                    parkedAngleDeg + elapsed * activeDegreesPerMs
                } else {
                    parkedAngleDeg
                }
            }
        }
        return normalise(angle)
    }

    /**
     * Called whenever playback state changes. Starts the loop, stops it, or does nothing,
     * depending on [isPlaying] and on whether the current [options] want a driven spin.
     */
    fun onPlaybackStateChanged(
        context: Context,
        isPlaying: Boolean,
        options: TurntableOptions,
    ) {
        val wantsDrivenSpin =
            options.spinMode == WidgetSpinMode.SMOOTH || options.spinMode == WidgetSpinMode.TICK

        if (!isPlaying || !wantsDrivenSpin) {
            stop()
            return
        }
        // A change of speed or direction has to be parked and restarted, otherwise the new
        // rate would be applied retroactively to time already elapsed.
        if (running && activeDegreesPerMs != options.degreesPerMs) {
            stop()
        }
        start(context.applicationContext, options)
    }

    /** Stops the loop and parks the record where it currently is. */
    fun stop() {
        park()
        loop?.cancel()
        loop = null
    }

    /** Drops any cached disc frames, e.g. after the user changes the turntable's styling. */
    fun invalidateRendering() {
        TurntableRenderer.clearCache()
    }

    private fun start(context: Context, options: TurntableOptions) {
        if (running) return

        running = true
        activeDegreesPerMs = options.degreesPerMs
        spinStartedAtMs = SystemClock.elapsedRealtime()

        loop?.cancel()
        loop = scope.launch {
            val frameMs = frameIntervalFor(options)
            val power = context.getSystemService<PowerManager>()
            val manager = GlanceAppWidgetManager(context)
            val widget = TurntableWidget()

            var ids = manager.getGlanceIds(TurntableWidget::class.java)
            var framesUntilIdRefresh = ID_REFRESH_FRAMES

            try {
                while (isActive && running) {
                    // A widget behind a locked or off screen is not worth a frame, and
                    // battery saver is an explicit request to stop doing things like this.
                    // Back right off rather than continuing to tick at frame rate.
                    if (power != null && (!power.isInteractive || power.isPowerSaveMode)) {
                        delay(IDLE_POLL_MS)
                        continue
                    }

                    delay(frameMs)
                    ensureActive()

                    if (--framesUntilIdRefresh <= 0) {
                        ids = manager.getGlanceIds(TurntableWidget::class.java)
                        framesUntilIdRefresh = ID_REFRESH_FRAMES
                    }

                    // Nothing placed on a home screen means nothing to animate.
                    if (ids.isEmpty()) {
                        Timber.tag(TAG).d("No turntable widgets placed; stopping spin loop")
                        break
                    }

                    ids.forEach { id -> widget.update(context, id) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Turntable spin loop failed")
            } finally {
                park()
            }
        }
    }

    /**
     * Publish interval for the chosen speed: fast enough that each frame moves the record
     * about [DEGREES_PER_FRAME], clamped at both ends.
     */
    private fun frameIntervalFor(options: TurntableOptions): Long {
        if (options.spinMode == WidgetSpinMode.TICK) return TICK_FRAME_MS
        val degreesPerMs = kotlin.math.abs(options.degreesPerMs)
        if (degreesPerMs <= 0f) return MAX_FRAME_MS
        return (DEGREES_PER_FRAME / degreesPerMs).toLong().coerceIn(MIN_FRAME_MS, MAX_FRAME_MS)
    }

    /** Freezes the record at the angle it has reached, and marks the loop as not running. */
    @Synchronized
    private fun park() {
        if (!running) return
        val elapsed = SystemClock.elapsedRealtime() - spinStartedAtMs
        parkedAngleDeg = normalise(parkedAngleDeg + elapsed * activeDegreesPerMs)
        running = false
    }

    private fun normalise(angle: Float): Float {
        val wrapped = angle % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    private const val TAG = "TurntableSpin"
}
