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
    private val directCooldown = object : LinkedHashMap<String, Long>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 64
    }
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
            try {
                val started = System.nanoTime()
                // A native player request can finish without full watch-page extraction.
                // Hedge slow responses instead of adding their timeout to fallback latency.
                val skipDirect = forceRefresh || synchronized(directCooldown) { (directCooldown[id] ?: 0) > now }
                val resolved = if (skipDirect) {
                    "newpipe" to extractNewPipeStreams(id, now)
                } else resolveWithHedgedFallback(
                    direct = { innerTube.directStreams(id).takeIf { it.isNotEmpty() }?.let { "visionos" to it } },
                    fallback = { extractNewPipeStreams(id, now).takeIf { it.isNotEmpty() }?.let { "newpipe" to it } }
                )
                val streams = resolved?.second.orEmpty()
                ensureActive()
                Timber.tag("StreamingLatency").d("manifest_provider=%s manifest_network_ms=%d streams=%d",
                    resolved?.first ?: "none", (System.nanoTime() - started) / 1_000_000, streams.size)
                if (streams.isNotEmpty()) synchronized(manifests) { manifests[id] = streams }
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
            YouTubeAudioStream(stream.content, mimeType, bitrateBps, expiry, contentLength)
        }.distinctBy { it.url }.sortedByDescending { it.bitrate }
    }

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
    }

    /**
     * Drops the cached manifest. [penalizeDirect] also sends the next lookups to full
     * extraction for a while; use it only when a URL from the direct client was rejected.
     */
    fun invalidate(videoId: String, penalizeDirect: Boolean = true) {
        val id = videoId.removePrefix("yt_")
        synchronized(manifests) { manifests.remove(id) }
        if (penalizeDirect) {
            synchronized(directCooldown) { directCooldown[id] = System.currentTimeMillis() + 120_000 }
        }
    }
}
