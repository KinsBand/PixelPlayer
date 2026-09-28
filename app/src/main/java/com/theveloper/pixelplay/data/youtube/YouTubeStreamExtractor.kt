package com.theveloper.pixelplay.data.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native host-engine boundary matching Spotube's YouTube audio plugin:
 * search returns video IDs; streamManifest returns audio-only renditions for the chosen ID.
 * The Hetu plugin itself requires Spotube's Flutter runtime and is not loaded by this app.
 */
@Singleton
class YouTubeStreamExtractor @Inject constructor(private val innerTube: InnerTubeClient) {
    /** Which clients work lately, and how fast; orders and paces every lookup. */
    internal val health = StreamClientHealth()

    /** The client whose manifest is cached / was last used per video, to blame it on a 403. */
    private val lastClient = object : LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 128
    }

    /** Direct (no JavaScript) manifest sources, in preferred order when equally healthy. */
    private val directClients: Map<String, suspend (String) -> List<YouTubeAudioStream>> = linkedMapOf(
        StreamClients.VISIONOS to { id -> innerTube.directStreams(id) },
        StreamClients.VISIONOS_MUSIC to { id -> innerTube.musicDirectStreams(id) },
    )
    private val locks = com.theveloper.pixelplay.utils.KeyedMutex<String>()
    private val related = object : LinkedHashMap<String, List<org.schabi.newpipe.extractor.stream.StreamInfoItem>>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<org.schabi.newpipe.extractor.stream.StreamInfoItem>>) = size > 32
    }.also { map ->
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("yt-related") { synchronized(map) { map.clear() } }
    }
    /** One related-page parse at a time: each watch page is parsed into a large JSON tree. */
    private val relatedGate = kotlinx.coroutines.sync.Semaphore(1)

    suspend fun relatedSongs(videoId: String): List<org.schabi.newpipe.extractor.stream.StreamInfoItem> {
        val id = videoId.removePrefix("yt_")
        synchronized(related) { related[id] }?.let { return it }
        if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return emptyList()
        // Recommendations are optional and must never delay or break audio extraction,
        // so skip them when the heap is already tight instead of risking an OOM crash.
        if (!MemoryHeadroom.hasAtLeast(RELATED_MIN_FREE_BYTES)) return emptyList()
        return relatedGate.withPermit {
            synchronized(related) { related[id] }?.let { return@withPermit it }
            if (!MemoryHeadroom.hasAtLeast(RELATED_MIN_FREE_BYTES)) return@withPermit emptyList()
            withContext(Dispatchers.IO) {
                val items = try {
                    NewPipeExecution.run(NewPipeExecution.Lane.BACKGROUND) {
                        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$id")
                        extractor.fetchPage()
                        extractor.relatedItems?.items.orEmpty()
                            .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                    }
                } catch (e: OutOfMemoryError) {
                    // The half-built parse tree is garbage now; drop the optional result and move on.
                    Timber.tag("YouTubeStreamExtractor").w("Skipped related songs for %s: low memory", id)
                    return@withContext emptyList()
                }
                ensureActive()
                synchronized(related) { related[id] = items }
                items
            }
        }
    }
    private val manifests = object : LinkedHashMap<String, List<YouTubeAudioStream>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<YouTubeAudioStream>>): Boolean = size > 64
    }
    /** Bumped on every network change; a lookup that straddles one isn't cached. */
    @Volatile private var networkGeneration = 0

    /**
     * Signed stream URLs are bound to the client's IP address, so after the default network
     * changes every cached one would be refused (HTTP 403), and each refusal also sent its song
     * to full extraction for two minutes. Drop them, and cooldowns earned on the old network;
     * the next lookup is one direct request instead.
     */
    fun onNetworkChanged() {
        networkGeneration++
        synchronized(manifests) { manifests.clear() }
        health.onNetworkChanged()
    }

    suspend fun streamManifest(videoId: String, forceRefresh: Boolean = false): List<YouTubeAudioStream> = withContext(Dispatchers.IO) {
        val id = videoId.removePrefix("yt_")
        if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return@withContext emptyList()
        locks.withKey(id) {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                synchronized(manifests) { manifests[id] }?.filter { it.expiresAt > now }
                    ?.takeIf { it.isNotEmpty() }?.let { return@withKey it }
            }
            synchronized(manifests) { manifests.remove(id) }
            val generation = networkGeneration
            try {
                val started = System.nanoTime()
                val resolved = raceHedged(attemptsFor(id, now, fullExtractionOnly = forceRefresh)) { client, outcome ->
                    record(client, outcome)
                }
                val streams = resolved?.second.orEmpty()
                ensureActive()
                Timber.tag("StreamingLatency").d("manifest_provider=%s manifest_network_ms=%d streams=%d",
                    resolved?.first ?: "none", (System.nanoTime() - started) / 1_000_000, streams.size)
                com.theveloper.pixelplay.data.diagnostics.PlaybackTrace.mark("manifest", resolved?.first ?: "none")
                if (resolved != null) synchronized(lastClient) { lastClient[id] = resolved.first }
                if (streams.isNotEmpty() && generation == networkGeneration) synchronized(manifests) { manifests[id] = streams }
                streams
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                // Parsing the watch page ran out of heap. Its tree is unreachable now, so fail this
                // lookup (the player retries / falls back) instead of crashing the whole app.
                Timber.tag("YouTubeStreamExtractor").w("Audio manifest skipped for %s: low memory", id)
                emptyList()
            } catch (e: Exception) {
                Timber.tag("YouTubeStreamExtractor").w(e, "Audio manifest unavailable for %s", id)
                emptyList()
            }
        }
    }

    private suspend fun extractNewPipeStreams(id: String, now: Long): List<YouTubeAudioStream> {
        val audioStreams = NewPipeExecution.run {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$id")
            extractor.fetchPage()
            extractor.audioStreams.orEmpty()
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return audioStreams.mapNotNull { stream ->
            val mimeType = stream.format?.mimeType ?: return@mapNotNull null
            if (!stream.isUrl || mimeType !in setOf("audio/mp4", "audio/webm")) return@mapNotNull null
            // DASH/HLS/OTF cannot be proxied as progressive byte ranges.
            if (stream.deliveryMethod != DeliveryMethod.PROGRESSIVE_HTTP) return@mapNotNull null
            val expiry = YouTubeAudioStream.expiry(stream.content, now)
            if (expiry <= now) return@mapNotNull null
            val bitrateBps = when {
                stream.averageBitrate > 0 -> stream.averageBitrate * 1000
                (stream.itagItem?.bitrate ?: 0) > 0 -> stream.itagItem!!.bitrate
                else -> stream.bitrate.coerceAtLeast(0)
            }
            val contentLength = YouTubeHttp.contentLengthOf(stream.content)
                ?: stream.itagItem?.contentLength?.takeIf { it > 0 } ?: -1L
            YouTubeAudioStream(stream.content, mimeType, bitrateBps, expiry, contentLength, StreamClients.NEWPIPE)
        }.distinctBy { it.url }.sortedByDescending { it.bitrate }
    }

    /** Opens the manifest host's connection so the first tap needs only the player request. */
    suspend fun warmUp() = innerTube.warmUpPlayback()

    suspend fun getStream(
        videoId: String,
        quality: AudioQualityPreset = AudioQualityPreset.AUTO,
        forceRefresh: Boolean = false,
    ): YouTubeAudioStream? = YouTubeAudioStream.select(streamManifest(videoId, forceRefresh), quality)

    suspend fun getStreamUrl(videoId: String, quality: AudioQualityPreset = AudioQualityPreset.AUTO): String? =
        getStream(videoId, quality)?.url

    private companion object {
        /** A watch-page parse can briefly need tens of MB of heap. */
        const val RELATED_MIN_FREE_BYTES = 48L * 1024 * 1024
        /** Full extraction downloads and parses a watch page; on a weak signal that takes a while. */
        const val FULL_EXTRACTION_TIMEOUT_MS = 20_000L
        /** A refused URL whose client isn't known skips direct clients this long (the old cooldown). */
        const val UNKNOWN_CLIENT_EXCLUSION_MS = 120_000L
    }

    /**
     * Drops the cached manifest. [penalizeDirect] means its URL was refused: the client that
     * produced it is then skipped for this song for a while, so the retry goes to the next
     * client (the other direct one first, full extraction last) instead of the same one.
     */
    fun invalidate(videoId: String, penalizeDirect: Boolean = true) =
        dropManifest(videoId, if (penalizeDirect) StreamClientHealth.Failure.MEDIA_REJECTED else null)

    /** The URL for [videoId] never sent a first byte: same as a refused one, but counted as a stall. */
    fun reportStall(videoId: String) = dropManifest(videoId, StreamClientHealth.Failure.STALL)

    private fun dropManifest(videoId: String, failure: StreamClientHealth.Failure?) {
        val id = videoId.removePrefix("yt_")
        synchronized(manifests) { manifests.remove(id) }
        if (failure == null) return
        val client = synchronized(lastClient) { lastClient[id] }
        if (client == null) {
            // Nothing known about where the URL came from: skip the direct clients, as before.
            StreamClients.DIRECT.forEach { health.excludeForVideo(id, it, UNKNOWN_CLIENT_EXCLUSION_MS) }
            return
        }
        health.recordFailure(client, failure)
        health.excludeForVideo(id, client)
    }

    /**
     * The race for one lookup: the healthiest direct client at once, the next one when the
     * first is slower than it usually is (or failed), and full extraction when both are stuck.
     * Clients that failed for this song recently are left out, unless that leaves nothing.
     */
    private fun attemptsFor(id: String, now: Long, fullExtractionOnly: Boolean): List<HedgedAttempt<List<YouTubeAudioStream>>> {
        var excluded = health.excludedFor(id)
        if (excluded.containsAll(StreamClients.DIRECT + StreamClients.NEWPIPE)) {
            health.clearExclusions(id)
            excluded = emptySet()
        }
        val direct = if (fullExtractionOnly) emptyList()
            else health.order(directClients.keys.filter { it !in excluded })
        val attempts = direct.mapIndexed { index, client ->
            HedgedAttempt(
                name = client,
                startAfterMs = if (index == 0) 0L else health.secondaryDelayMs(direct.first()),
                timeoutMs = health.directTimeoutMs(client),
                block = { directClients.getValue(client)(id).takeIf { it.isNotEmpty() } },
            )
        }
        if (StreamClients.NEWPIPE in excluded && attempts.isNotEmpty()) return attempts
        return attempts + HedgedAttempt(
            name = StreamClients.NEWPIPE,
            startAfterMs = direct.firstOrNull()?.let(health::fallbackDelayMs) ?: 0L,
            timeoutMs = FULL_EXTRACTION_TIMEOUT_MS,
            block = { extractNewPipeStreams(id, now).takeIf { it.isNotEmpty() } },
        )
    }

    private fun record(client: String, outcome: HedgedOutcome) {
        when (outcome) {
            is HedgedOutcome.Success -> health.recordSuccess(client, outcome.elapsedMs)
            is HedgedOutcome.Empty -> health.recordFailure(client, StreamClientHealth.Failure.EMPTY)
            is HedgedOutcome.TimedOut -> health.recordFailure(client, StreamClientHealth.Failure.ERROR)
            // Being offline says nothing about a client.
            is HedgedOutcome.Failed -> if (!isOffline(outcome.error)) {
                health.recordFailure(client, StreamClientHealth.Failure.ERROR)
            }
        }
    }

    private fun isOffline(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is java.net.UnknownHostException || cause is java.net.ConnectException ||
                cause is java.net.NoRouteToHostException) return true
            cause = cause.cause
        }
        return false
    }
}
