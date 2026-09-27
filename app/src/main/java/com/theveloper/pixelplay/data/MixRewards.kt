package com.theveloper.pixelplay.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What a finished play says about a song, on one scale for the whole engine.
 *
 * | Play | Reward |
 * | --- | --- |
 * | Skipped before 25 % or within 30 s | −1.0 |
 * | Skipped at 25–50 % | −0.4 |
 * | Listened 50–80 % | +0.4 |
 * | Listened ≥ 80 % (≥ 65 % when it ended on its own, e.g. a crossfade) | +1.0 |
 * | …chosen by the listener rather than the mix | × 1.5 |
 * | Replayed (≥ 20 s or a quarter of it heard again) | + 1.5 |
 * | "Not for this mix" / "Exclude" | −1.5 |
 * | Error, interruption, unknown ending | no label |
 *
 * Earlier versions gave a full mix song +0.35 and an early skip −0.2 and ignored everything in
 * between, so a whole session of skips barely moved the taste model.
 */
internal object MixRewards {
    const val EARLY_SKIP_COVERAGE = 0.25
    const val EARLY_SKIP_MS = 30_000L

    fun isEarlySkip(attempt: MixAttempt): Boolean =
        attempt.endReason == MixEndReason.SKIP.name &&
            (attempt.coverage < EARLY_SKIP_COVERAGE || attempt.activeMs < EARLY_SKIP_MS)

    /** The reward for [attempt], or null when the play says nothing (error, interruption…). */
    fun reward(attempt: MixAttempt): Double? {
        when (attempt.endReason) {
            MixEndReason.DISLIKE.name -> return -1.5
            MixEndReason.NATURAL.name, MixEndReason.SKIP.name -> Unit
            else -> return null
        }
        val coverage = attempt.coverage
        val natural = attempt.endReason == MixEndReason.NATURAL.name
        var reward = when {
            isEarlySkip(attempt) -> -1.0
            !natural && coverage < 0.5 -> -0.4
            coverage >= 0.8 || (natural && coverage >= 0.65) -> 1.0
            coverage >= 0.5 -> 0.4
            else -> 0.0
        }
        if (reward > 0 && attempt.voluntary) reward *= 1.5
        val replayed = attempt.durationMs > 0 && attempt.repeatedMs >= maxOf(20_000L, attempt.durationMs / 4)
        if (replayed) reward = maxOf(reward, 0.0) + 1.5
        return reward
    }

    /** A draw from Beta(α, β) (α, β > 0), or its mean when [random] is null. */
    fun sampleBeta(alpha: Double, beta: Double, random: Random?): Double {
        val a = alpha.coerceAtLeast(1e-3)
        val b = beta.coerceAtLeast(1e-3)
        if (random == null) return a / (a + b)
        val x = sampleGamma(a, random)
        val y = sampleGamma(b, random)
        return if (x + y <= 0.0) a / (a + b) else x / (x + y)
    }

    /** Marsaglia–Tsang gamma sampler (shape k, scale 1); shapes below 1 use the boost trick. */
    private fun sampleGamma(shape: Double, random: Random): Double {
        if (shape < 1.0) {
            val u = random.nextDouble().coerceAtLeast(1e-12)
            return sampleGamma(shape + 1.0, random) * u.pow(1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = gaussian(random)
                v = 1.0 + c * x
            } while (v <= 0.0)
            v = v * v * v
            val u = random.nextDouble().coerceAtLeast(1e-12)
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v
            if (ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) return d * v
        }
    }

    /** Standard normal (Box–Muller). */
    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    /** exp(−age / window), for fading old evidence. */
    fun fade(now: Long, at: Long, windowMs: Double): Double = exp(-(now - at).coerceAtLeast(0L).toDouble() / windowMs)
}
