package com.theveloper.pixelplay.data.youtube

/**
 * What each stream client (a way of getting an audio manifest: a direct InnerTube client or
 * NewPipe's full extraction) has done lately, so lookups try the one most likely to work first
 * and hedge after a delay learned from its real latency instead of a fixed guess.
 *
 * Modelled on Metrolist's InnerTubeX `ClientHealthMonitor` (score adjustments per client) and
 * its per-video failed-client exclusion, plus "hedged requests" from *The Tail at Scale*: the
 * backup starts once the primary is slower than it usually is (a high percentile of its own
 * latency), so the backup rarely runs but still caps the slow tail.
 *
 * - A success raises a client's score; failures lower it. A client that failed twice in a row
 *   (or whose score sank low) is tried after the others; three failures in a row put it on a
 *   cooldown (1, 2, 4 … up to [MAX_COOLDOWN_MS]) during which it is tried last. A single failure
 *   (often specific to one song) doesn't change the order.
 * - A client whose URL was rejected or stalled for a song is excluded for that song for
 *   [VIDEO_EXCLUSION_MS], so the retry goes to a different client instead of the same one.
 * - Offline errors are not failures of a client and are never recorded as such.
 *
 * Thread-safe; all state is in memory and resets with the process or a network change.
 */
class StreamClientHealth(private val clock: () -> Long = System::currentTimeMillis) {

    enum class Failure(internal val weight: Double) {
        /** The client answered but had no playable audio for this song. */
        EMPTY(0.5),
        /** The request failed or timed out. */
        ERROR(1.0),
        /** Its stream URL was refused by the CDN (HTTP 401/403/410). */
        MEDIA_REJECTED(1.0),
        /** Its stream URL never sent a first byte. */
        STALL(1.0),
    }

    private class State {
        /** Exponentially weighted success rate, 0..1. Starts optimistic. */
        var score = 1.0
        var consecutiveFailures = 0
        var cooldownUntil = 0L
        var cooldownMs = 0L
        val latencies = LongArray(LATENCY_SAMPLES)
        var latencyCount = 0
        var latencyNext = 0
    }

    private val lock = Any()
    private val states = HashMap<String, State>()
    /** videoId -> (client -> excluded until). */
    private val exclusions = object : LinkedHashMap<String, MutableMap<String, Long>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableMap<String, Long>>) = size > 128
    }

    private fun state(client: String) = states.getOrPut(client) { State() }

    fun recordSuccess(client: String, latencyMs: Long) = synchronized(lock) {
        val state = state(client)
        state.score = state.score * (1 - ALPHA) + ALPHA
        state.consecutiveFailures = 0
        state.cooldownUntil = 0L
        state.cooldownMs = 0L
        if (latencyMs >= 0) {
            state.latencies[state.latencyNext] = latencyMs
            state.latencyNext = (state.latencyNext + 1) % LATENCY_SAMPLES
            state.latencyCount = minOf(state.latencyCount + 1, LATENCY_SAMPLES)
        }
    }

    fun recordFailure(client: String, failure: Failure) = synchronized(lock) {
        val state = state(client)
        state.score = state.score * (1 - ALPHA * failure.weight)
        state.consecutiveFailures++
        if (state.consecutiveFailures >= COOLDOWN_AFTER_FAILURES) {
            state.cooldownMs = (state.cooldownMs * 2).coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
            state.cooldownUntil = clock() + state.cooldownMs
            state.consecutiveFailures = 0
        }
    }

    /** Whether [client] is resting after repeated failures. */
    fun isCoolingDown(client: String): Boolean = synchronized(lock) {
        (states[client]?.cooldownUntil ?: 0L) > clock()
    }

    /**
     * [clients] best first: resting clients last, clients that keep failing next to last. The
     * sort is stable, so clients in good standing keep the given (preferred) order.
     */
    fun order(clients: List<String>): List<String> = synchronized(lock) {
        val now = clock()
        clients.sortedWith(
            compareBy<String> { (states[it]?.cooldownUntil ?: 0L) > now }
                .thenBy { states[it]?.let { state -> isStruggling(state) } ?: false }
        )
    }

    private fun isStruggling(state: State): Boolean =
        state.consecutiveFailures >= DEMOTE_AFTER_FAILURES || state.score < DEMOTE_BELOW_SCORE

    /** The [percentile] (0..100) of [client]'s recent successful latencies, or null with too few. */
    fun latencyPercentile(client: String, percentile: Int): Long? = synchronized(lock) {
        val state = states[client] ?: return null
        if (state.latencyCount < MIN_SAMPLES) return null
        val sorted = state.latencies.copyOf(state.latencyCount).also { it.sort() }
        val index = ((percentile.coerceIn(0, 100) / 100.0) * (sorted.size - 1)).toInt()
        sorted[index]
    }

    /** When to start the next direct client if [primary] hasn't answered. */
    fun secondaryDelayMs(primary: String): Long =
        latencyPercentile(primary, 90)?.let { (it + 50).coerceIn(MIN_SECONDARY_DELAY_MS, MAX_SECONDARY_DELAY_MS) }
            ?: DEFAULT_SECONDARY_DELAY_MS

    /**
     * When to start full extraction (a far heavier request) if no direct client has answered.
     * Needs more patience than [secondaryDelayMs]: it only pays off when direct is really stuck.
     */
    fun fallbackDelayMs(primary: String): Long =
        latencyPercentile(primary, 95)?.let { (it * 3 / 2).coerceIn(MIN_FALLBACK_DELAY_MS, MAX_FALLBACK_DELAY_MS) }
            ?: DEFAULT_FALLBACK_DELAY_MS

    /** How long a direct request may take before it counts as failed. */
    fun directTimeoutMs(client: String): Long =
        latencyPercentile(client, 95)?.let { (it * 3).coerceIn(MIN_DIRECT_TIMEOUT_MS, MAX_DIRECT_TIMEOUT_MS) }
            ?: DEFAULT_DIRECT_TIMEOUT_MS

    /** Don't use [client] for [videoId] for a while (its URL was refused or stalled). */
    fun excludeForVideo(videoId: String, client: String, durationMs: Long = VIDEO_EXCLUSION_MS) = synchronized(lock) {
        exclusions.getOrPut(videoId) { HashMap() }[client] = clock() + durationMs
    }

    /** Clients currently excluded for [videoId]. */
    fun excludedFor(videoId: String): Set<String> = synchronized(lock) {
        val map = exclusions[videoId] ?: return emptySet()
        val now = clock()
        map.entries.removeAll { it.value <= now }
        if (map.isEmpty()) exclusions.remove(videoId)
        map.keys.toSet()
    }

    fun clearExclusions(videoId: String) = synchronized(lock) { exclusions.remove(videoId); Unit }

    /**
     * A new network: cooldowns, exclusions and latencies measured on the old one say nothing
     * about this one. Scores are kept, but pulled back towards neutral.
     */
    fun onNetworkChanged() = synchronized(lock) {
        exclusions.clear()
        states.values.forEach { state ->
            state.cooldownUntil = 0L
            state.cooldownMs = 0L
            state.consecutiveFailures = 0
            state.latencyCount = 0
            state.latencyNext = 0
            state.score = (state.score + 1.0) / 2
        }
    }

    companion object {
        private const val ALPHA = 0.3
        private const val LATENCY_SAMPLES = 20
        private const val MIN_SAMPLES = 5
        const val DEMOTE_AFTER_FAILURES = 2
        private const val DEMOTE_BELOW_SCORE = 0.5
        const val COOLDOWN_AFTER_FAILURES = 3
        const val MIN_COOLDOWN_MS = 60_000L
        const val MAX_COOLDOWN_MS = 30L * 60_000
        const val VIDEO_EXCLUSION_MS = 5L * 60_000

        const val DEFAULT_SECONDARY_DELAY_MS = 250L
        const val MIN_SECONDARY_DELAY_MS = 150L
        const val MAX_SECONDARY_DELAY_MS = 1_000L

        const val DEFAULT_FALLBACK_DELAY_MS = 600L
        const val MIN_FALLBACK_DELAY_MS = 350L
        const val MAX_FALLBACK_DELAY_MS = 2_500L

        const val DEFAULT_DIRECT_TIMEOUT_MS = 2_500L
        const val MIN_DIRECT_TIMEOUT_MS = 1_500L
        const val MAX_DIRECT_TIMEOUT_MS = 6_000L
    }
}
