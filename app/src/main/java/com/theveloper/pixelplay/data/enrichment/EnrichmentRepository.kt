package com.theveloper.pixelplay.data.enrichment

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.theveloper.pixelplay.data.analysis.AudioAnalysisEngine
import com.theveloper.pixelplay.data.analysis.PcmAudio
import com.theveloper.pixelplay.data.analysis.PcmDecoder
import com.theveloper.pixelplay.data.analysis.dsp.ChromaprintJni
import com.theveloper.pixelplay.data.analysis.dsp.BeatGridDetector
import com.theveloper.pixelplay.data.analysis.dsp.EnergyLoudness
import com.theveloper.pixelplay.data.analysis.dsp.OnsetEnvelope
import com.theveloper.pixelplay.data.analysis.dsp.SilenceDetector
import com.theveloper.pixelplay.data.analysis.ml.MusicMlEngine
import com.theveloper.pixelplay.data.database.ArtworkEntity
import com.theveloper.pixelplay.data.database.EnrichmentDao
import com.theveloper.pixelplay.data.database.ExternalIdEntity
import com.theveloper.pixelplay.data.database.MetadataProvenance
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.ProvenanceSource
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.TrackAnalysisEntity
import com.theveloper.pixelplay.data.database.TrackEditorialEntity
import com.theveloper.pixelplay.data.database.TrackEmbeddingEntity
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.data.network.acoustid.AcoustIdService
import com.theveloper.pixelplay.data.network.spotify.SpotifyService
import com.theveloper.pixelplay.data.network.lastfm.LastFmRepository
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.utils.AlbumArtUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Per-song result of the enrichment pipeline. `error != null` marks a failed song. */
data class EnrichmentOutcome(
    val songId: Long,
    /** Online stage produced/refreshed data (or an editorial row already existed). */
    val online: Boolean,
    /** An analysis row exists after the run (already present or newly computed). */
    val analysis: Boolean,
    /** Lyrics are available after the run (already present or newly fetched). */
    val lyrics: Boolean,
    val error: String? = null
)

/** Aggregate of a batch run. Skips are not errors: success == error == null. */
data class EnrichmentBatchResult(
    val processed: Int,
    val succeeded: Int,
    val failed: Int
)

/**
 * Orchestrates the three enrichment stages for library tracks:
 *
 * 1. **Online** (MusicBrainz + Cover Art Archive + Last.fm): external ids,
 *    genres/tags, editorial metadata, downloaded artwork. Writes an editorial
 *    row as soon as a recording matches (fields nullable) so "missing" mode
 *    does not re-hammer already-matched tracks.
 * 2. **Analysis** (local DSP): BPM/key/loudness/energy/waveform into
 *    `track_analysis`. Never runs for http(s) streams.
 * 3. **Lyrics**: delegates to [LyricsRepository], which persists LRCLIB
 *    results itself; this stage only triggers the fetch when nothing is
 *    stored yet.
 *
 * Every stage is individually guarded: a failure in one never aborts the
 * others, and per-song failures never abort a batch.
 */
@Singleton
class EnrichmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val enrichmentDao: EnrichmentDao,
    private val musicBrainzRepository: MusicBrainzRepository,
    private val lastFmRepository: LastFmRepository,
    private val audioAnalysisEngine: AudioAnalysisEngine,
    private val lyricsRepository: LyricsRepository,
    private val networkAccessPolicy: NetworkAccessPolicy,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val spotifyService: SpotifyService,
    private val acoustIdService: AcoustIdService,
    private val musicMlEngine: MusicMlEngine,
    private val metadataStore: com.theveloper.pixelplay.data.metadata.SongMetadataStore,
    private val json: Json
) {

    /**
     * Enriches a single song. @param forceOnline re-runs the online stage even
     * when an editorial row exists; @param forceAnalysis re-runs the DSP stage
     * even when an analysis row exists.
     */
    suspend fun enrichSong(
        songId: Long,
        forceOnline: Boolean = false,
        forceAnalysis: Boolean = false
    ): EnrichmentOutcome {
        val song = musicDao.getSongByIdOnce(songId)
            ?: return EnrichmentOutcome(songId, online = false, analysis = false, lyrics = false, error = "song not found")
        return enrichSongEntity(song, forceOnline, forceAnalysis)
    }

    /**
     * Enriches many songs one by one; a failing song never aborts the batch.
     * [onProgress] runs before each song and once more at the very end.
     */
    suspend fun enrichBatch(
        songIds: List<Long>,
        forceOnline: Boolean = false,
        forceAnalysis: Boolean = false,
        onProgress: suspend (processed: Int, total: Int, currentTitle: String) -> Unit = { _, _, _ -> }
    ): EnrichmentBatchResult {
        val total = songIds.size
        var succeeded = 0
        var failed = 0

        songIds.forEachIndexed { index, songId ->
            coroutineContext.ensureActive()
            // Fetch once here so the batch has the title for progress reporting
            // and the shared path does not hit the DB a second time.
            val song = runCatching { musicDao.getSongByIdOnce(songId) }.getOrNull()
            onProgress(index, total, song?.title ?: "")

            val outcome = runCatching {
                if (song == null) {
                    EnrichmentOutcome(songId, online = false, analysis = false, lyrics = false, error = "song not found")
                } else {
                    enrichSongEntity(song, forceOnline, forceAnalysis)
                }
            }.getOrElse { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                EnrichmentOutcome(songId, online = false, analysis = false, lyrics = false,
                    error = throwable.message ?: throwable.javaClass.simpleName)
            }

            if (outcome.error == null) succeeded++ else failed++
        }

        onProgress(total, total, "")
        return EnrichmentBatchResult(processed = total, succeeded = succeeded, failed = failed)
    }

    // ─── Shared per-song pipeline ────────────────────────────────────────

    private suspend fun enrichSongEntity(
        song: SongEntity,
        forceOnline: Boolean,
        forceAnalysis: Boolean
    ): EnrichmentOutcome {
        coroutineContext.ensureActive()
        // FK parents for every enrichment row written below.
        enrichmentDao.ensureTrackRows(song.id)

        // The two stages that need decoded audio (AcoustID fingerprint, DSP analysis) run under
        // one app-wide gate and share a single decode: each decode is up to ~16 MB of floats
        // plus the analysers' working arrays, and three copies (fingerprint, head, tail) of two
        // songs at once (worker batch + song-info sheet) could take ~150 MB of a 256 MB heap
        // while music was playing.
        val (online, analysis) = PCM_GATE.withPermit {
            val pcm = SharedPcm(song)
            try {
                runOnlineStage(song, forceOnline, pcm) to runAnalysisStage(song, forceAnalysis, pcm)
            } finally {
                pcm.release()
            }
        }
        val lyrics = runLyricsStage(song)

        Timber.tag(TAG).d(
            "enrich #%d '%s': online=%s analysis=%s lyrics=%s",
            song.id, song.title, online, analysis, lyrics
        )
        return EnrichmentOutcome(song.id, online = online, analysis = analysis, lyrics = lyrics)
    }

    // ─── Stage 1: online metadata ────────────────────────────────────────

    /**
     * Lazily decodes [song] once for both stages. Skips (returns null) when the heap does not
     * have room for a decode, and turns an [OutOfMemoryError] during the decode into a skip:
     * analysis is optional and is retried on the next enrichment pass, a crash is not.
     */
    private inner class SharedPcm(private val song: SongEntity) {
        private var loaded = false
        private var value: PcmAudio? = null

        suspend fun get(): PcmAudio? {
            if (loaded) return value
            loaded = true
            val uri = resolvePlayableUri(song) ?: return null
            if (com.theveloper.pixelplay.data.diagnostics.HeapPressure.isElevated() ||
                !com.theveloper.pixelplay.data.youtube.MemoryHeadroom.hasAtLeast(PCM_MIN_HEADROOM_BYTES)
            ) {
                Timber.tag(TAG).w("PCM decode for #%d skipped: low heap headroom", song.id)
                return null
            }
            value = try {
                PcmDecoder.decode(context, uri)
            } catch (oom: OutOfMemoryError) {
                Timber.tag(TAG).w("PCM decode for #%d skipped: out of memory", song.id)
                null
            }
            return value
        }

        fun release() {
            value = null
        }
    }

    private suspend fun runOnlineStage(song: SongEntity, forceOnline: Boolean, sharedPcm: SharedPcm): Boolean {
        val alreadyDone = enrichmentDao.getEditorial(song.id) != null
        if (!forceOnline && alreadyDone) return true
        if (networkAccessPolicy.getDecision(NetworkPurpose.Enrichment) != NetworkDecision.Allowed) {
            Timber.tag(TAG).d("online stage skipped for #%d (network policy)", song.id)
            return alreadyDone
        }
        if (!wifiOnlySatisfied()) {
            Timber.tag(TAG).d("online stage skipped for #%d (wifi-only, on metered network)", song.id)
            return alreadyDone
        }

        val provenance = mutableListOf<MetadataProvenance>()
        val now = System.currentTimeMillis()
        var matched = false

        // AcoustID lookup using fingerprint fallback
        var acoustIdMbid: String? = null
        runCatching {
            val uri = resolvePlayableUri(song)
            if (uri != null) {
                val pcm = sharedPcm.get()
                val durationSec = (song.duration / 1000).toInt()
                if (pcm != null) {
                    val fingerprint = ChromaprintJni.generateFingerprint(pcm.samples, pcm.sampleRate)
                    if (fingerprint != null) {
                        acoustIdMbid = acoustIdService.lookupFingerprint(durationSec, fingerprint)
                    }
                }
            }
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag(TAG).w("AcoustID generation or lookup failed: %s", e.message)
        }

        // MusicBrainz: search -> one recording lookup -> ids/genres/editorial.
        runCatching {
            val mbid = acoustIdMbid ?: musicBrainzRepository.searchRecording(
                title = song.title,
                artist = song.artistName,
                album = song.albumName,
                durationMs = song.duration
            )?.recordingMbid

            if (mbid == null) return@runCatching
            matched = true
            upsertExternalId(song.id, PROVIDER_MB_RECORDING, mbid)

            val recording = musicBrainzRepository.lookupRecordingModel(mbid)
            val details = recording?.let { musicBrainzRepository.toRecordingDetails(it) }

            if (details != null) {
                details.isrcs.firstOrNull()?.let { isrc ->
                    upsertExternalId(song.id, PROVIDER_ISRC, isrc)
                }
                details.genres.forEach { genre ->
                    runCatching { enrichmentDao.linkGenreToTrack(song.id, genre) }
                }
                details.tags.forEach { tag ->
                    runCatching { enrichmentDao.linkTagToTrack(song.id, tag) }
                }
                if (details.genres.isNotEmpty()) {
                    provenance += MetadataProvenance(FIELD_GENRES, ProvenanceSource.API, now)
                }
                if (details.tags.isNotEmpty()) {
                    provenance += MetadataProvenance(FIELD_TAGS, ProvenanceSource.API, now)
                }
                // Only fill the legacy flat column when the file itself had none.
                if (song.genre.isNullOrBlank()) {
                    details.genres.firstOrNull()?.let { firstGenre ->
                        runCatching { musicDao.updateGenreIfBlank(song.id, firstGenre) }
                        provenance += MetadataProvenance(FIELD_GENRE, ProvenanceSource.API, now)
                    }
                }
            }

            var releaseDate: String? = null
            var recordLabel: String? = null
            var country: String? = null
            var upcEan: String? = null
            var catalogueNumber: String? = null
            var releaseType: String? = null
            var language: String? = null

            // Work details lookup (Lyricist / Composer / Songwriter)
            if (details != null && details.workMbid != null) {
                runCatching {
                    val work = musicBrainzRepository.fetchWorkDetails(details.workMbid)
                    if (work != null) {
                        language = work.language
                    }
                }
            }

            val releaseMbid = recording?.let {
                musicBrainzRepository.pickBestRelease(it.releases, song.albumName)
            }
            if (releaseMbid != null) {
                upsertExternalId(song.id, PROVIDER_MB_RELEASE, releaseMbid)
                val release = musicBrainzRepository.fetchReleaseDetails(releaseMbid)
                if (release != null) {
                    releaseDate = release.date
                    recordLabel = release.label
                    country = release.country
                    upcEan = release.upcEan
                    catalogueNumber = release.catalogueNumber
                    releaseType = release.releaseType
                    if (releaseDate != null) provenance += MetadataProvenance(FIELD_RELEASE_DATE, ProvenanceSource.API, now)
                    if (recordLabel != null) provenance += MetadataProvenance(FIELD_RECORD_LABEL, ProvenanceSource.API, now)
                    if (country != null) provenance += MetadataProvenance(FIELD_COUNTRY, ProvenanceSource.API, now)
                }
                fetchAndStoreArtwork(song, releaseMbid, provenance, now)
            }

            // Editorial row is written for every matched recording (fields
            // nullable) so "missing" mode treats the track as processed.
            enrichmentDao.upsertEditorial(
                TrackEditorialEntity(
                    trackId = song.id,
                    description = null,
                    releaseDate = releaseDate,
                    recordLabel = recordLabel,
                    country = country,
                    upcEan = upcEan,
                    catalogueNumber = catalogueNumber,
                    releaseType = releaseType,
                    language = language
                )
            )
        }.onFailure { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            Timber.tag(TAG).w(throwable, "MusicBrainz stage failed for #%d", song.id)
        }

        val editorialSnapshot = enrichmentDao.getEditorial(song.id)
        if (editorialSnapshot != null) {
            val fields = mapOf("release.edition_release_date" to editorialSnapshot.releaseDate,
                "release.label" to editorialSnapshot.recordLabel, "release.country" to editorialSnapshot.country,
                "release.barcode" to editorialSnapshot.upcEan, "release.catalogue_number" to editorialSnapshot.catalogueNumber,
                "release.release_type" to editorialSnapshot.releaseType, "song.languages" to editorialSnapshot.language)
            metadataStore.record(song.toSong(), fields.filterValues { !it.isNullOrBlank() }.mapValues { (_, value) ->
                com.theveloper.pixelplay.data.metadata.MetadataClaim(value, "MusicBrainz", now)
            })
        }
        // Last.fm: extra tags + a description when the track has none.
        runCatching {
            val info = lastFmRepository.getTrackInfo(song.artistName, song.title) ?: return@runCatching
            info.tags.forEach { tag ->
                runCatching { enrichmentDao.linkTagToTrack(song.id, tag) }
            }
            if (info.tags.isNotEmpty() && provenance.none { it.field == FIELD_TAGS }) {
                provenance += MetadataProvenance(FIELD_TAGS, ProvenanceSource.API, now)
            }
            val current = enrichmentDao.getEditorial(song.id)
            if (current != null && current.description.isNullOrBlank() && !info.summary.isNullOrBlank()) {
                enrichmentDao.upsertEditorial(current.copy(description = info.summary))
                provenance += MetadataProvenance(FIELD_DESCRIPTION, ProvenanceSource.API, now)
            }
        }.onFailure { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            Timber.tag(TAG).w(throwable, "Last.fm stage failed for #%d", song.id)
        }

        if (provenance.isNotEmpty()) {
            mergeAndStoreProvenance(song.id, provenance)
        }
        return matched || enrichmentDao.getEditorial(song.id) != null
    }

    /** Cover Art Archive download -> shared album-art cache -> artwork row. */
    private suspend fun fetchAndStoreArtwork(
        song: SongEntity,
        releaseMbid: String,
        provenance: MutableList<MetadataProvenance>,
        now: Long
    ) {
        runCatching {
            val hasCaaArt = enrichmentDao.getArtworkForTrack(song.id).any { it.source == ARTWORK_SOURCE_CAA }
            if (hasCaaArt) return@runCatching
            val url = musicBrainzRepository.fetchCoverArtUrls(releaseMbid).firstOrNull() ?: return@runCatching
            val bytes = musicBrainzRepository.downloadCoverArt(url) ?: return@runCatching
            // Reuses the shared art cache (bounds/recompress/clear no-art marker
            // all handled inside); the songs table is never touched.
            val cachedUri = AlbumArtUtils.saveAlbumArtToCache(context, bytes, song.id)
            enrichmentDao.upsertArtwork(
                ArtworkEntity(
                    trackId = song.id,
                    albumId = song.albumId,
                    uri = cachedUri.toString(),
                    source = ARTWORK_SOURCE_CAA
                )
            )
            provenance += MetadataProvenance(FIELD_ARTWORK, ProvenanceSource.API, now)
        }.onFailure { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            Timber.tag(TAG).w(throwable, "artwork fetch failed for #%d", song.id)
        }
    }

    private suspend fun upsertExternalId(trackId: Long, provider: String, externalId: String) {
        if (externalId.isBlank()) return
        runCatching {
            enrichmentDao.upsertExternalId(
                ExternalIdEntity(trackId = trackId, providerName = provider, externalId = externalId)
            )
        }
    }

    // ─── Stage 2: local audio analysis ───────────────────────────────────

    private suspend fun runAnalysisStage(song: SongEntity, forceAnalysis: Boolean, sharedPcm: SharedPcm): Boolean {
        return try {
            runAnalysisStageInner(song, forceAnalysis, sharedPcm)
        } catch (oom: OutOfMemoryError) {
            // Only the analysers' temporary arrays are live here; they are unreachable once we
            // unwind, so the app can carry on. The song is analysed again on a later pass.
            Timber.tag(TAG).w("analysis for #%d skipped: out of memory", song.id)
            sharedPcm.release()
            false
        }
    }

    private suspend fun runAnalysisStageInner(song: SongEntity, forceAnalysis: Boolean, sharedPcm: SharedPcm): Boolean {
        val fileProbe = withContext(Dispatchers.IO) { com.theveloper.pixelplay.data.analysis.FileProber.probe(song.filePath) }
        val previous = enrichmentDao.getAnalysis(song.id)
        val previousTechnical = enrichmentDao.getTechnical(song.id)
        if (!forceAnalysis && previous?.analysisVersion == AudioAnalysisEngine.CURRENT_ANALYSIS_VERSION &&
            fileProbe.sha256Checksum != null && fileProbe.sha256Checksum == previousTechnical?.sha256Checksum) return true

        val uri = resolvePlayableUri(song) ?: return false
        val pcm = runCatching { sharedPcm.get() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull() ?: return false

        // File technical probing (checksum, file size)
        val analysedDurationMs = (pcm.durationSeconds * 1000).toLong()
        val coversWholeAsset = song.duration > 0 && analysedDurationMs >= song.duration - 1000

        // Save technical entity
        runCatching {
            enrichmentDao.upsertTechnical(
                com.theveloper.pixelplay.data.database.TrackTechnicalEntity(
                    trackId = song.id,
                    bitrate = song.bitrate ?: 0,
                    sampleRate = song.sampleRate ?: pcm.sampleRate,
                    mimeType = pcm.mimeType ?: song.mimeType,
                    containerFormat = song.mimeType,
                    bitDepth = pcm.bitDepth,
                    channelCount = pcm.channelCount,
                    lufsIntegrated = null, // Will update after DSP
                    dynamicRange = null,
                    replayGain = null,
                    fileSizeBytes = fileProbe.fileSize,
                    sha256Checksum = fileProbe.sha256Checksum
                )
            )
        }

        // 1. Core DSP analysis
        val result = withContext(Dispatchers.Default) { audioAnalysisEngine.analyze(pcm) }

        // 1b. End-of-track descriptors. The head window above stops at 180 s, so on
        // longer tracks decode the tail separately. Crossfades use the trailing
        // silence to start where the music actually ends, not where the file does,
        // and head + tail together give a usable loudness estimate.
        val tail = if (coversWholeAsset) null else analyseTail(uri, pcm, song.duration)
        val silenceEndMs: Long? = if (coversWholeAsset) result.silenceAtEndMs else tail?.silenceEndMs
        val lufsIntegrated: Float? = if (coversWholeAsset) result.lufsIntegrated else tail?.lufsIntegrated
        val replayGain: Float? = if (coversWholeAsset) {
            result.replayGain
        } else {
            lufsIntegrated?.let { EnergyLoudness.calculateReplayGain(it) }
        }

        // Update technical entity with LUFS & ReplayGain from DSP
        runCatching {
            val existingTech = enrichmentDao.getTechnical(song.id)
            if (existingTech != null) {
                enrichmentDao.upsertTechnical(
                    existingTech.copy(
                        lufsIntegrated = lufsIntegrated,
                        dynamicRange = result.dynamicRange.takeIf { coversWholeAsset },
                        replayGain = replayGain
                    )
                )
            }
        }

        // 2. Prepare ML Engine and run dynamic models if ready
        val mlReady = if (wifiOnlySatisfied() && networkAccessPolicy.getDecision(NetworkPurpose.Enrichment) == NetworkDecision.Allowed) {
            withContext(Dispatchers.IO) { musicMlEngine.prepare() }
        } else false
        if (mlReady) {
            val (mlGenre, mlMood) = withContext(Dispatchers.Default) { musicMlEngine.classifyGenreAndMood(pcm.samples, pcm.sampleRate) }
            mlGenre?.let { g -> runCatching { enrichmentDao.linkGenreToTrack(song.id, g) } }
            mlMood?.let { m -> runCatching { enrichmentDao.linkMoodToTrack(song.id, m) } }

            // Only real VGGish vectors are stored (null when the model isn't available).
            val embedding = withContext(Dispatchers.Default) { musicMlEngine.extractEmbedding(pcm.samples, pcm.sampleRate) }
            if (embedding != null) enrichmentDao.upsertEmbedding(TrackEmbeddingEntity(song.id, embedding))
        }

        // 3. Beat grid extraction
        val onset = withContext(Dispatchers.Default) { OnsetEnvelope.compute(pcm.samples, pcm.sampleRate) }
        val beats = if (result.bpm == null) emptyList<Float>() else BeatGridDetector.detectBeatGrid(
            onset = onset.envelope,
            envelopeSampleRate = onset.envelopeSampleRate,
            bpm = result.bpm,
            durationMs = analysedDurationMs
        )
        val beatGridJson = json.encodeToString(beats)

        // 4. Save/update analysis entity
        val currentAnalysis = enrichmentDao.getAnalysis(song.id)
        enrichmentDao.upsertAnalysis(
            TrackAnalysisEntity(
                trackId = song.id,
                bpm = result.bpm,
                musicKey = result.musicKey,
                keyCamelot = result.camelot,
                loudness = result.loudnessDb,
                energy = result.energy,
                valence = currentAnalysis?.valence,
                danceability = currentAnalysis?.danceability,
                acousticness = currentAnalysis?.acousticness,
                instrumentalness = currentAnalysis?.instrumentalness,
                speechiness = currentAnalysis?.speechiness,
                liveness = currentAnalysis?.liveness,
                beatGridJson = beatGridJson,
                waveform = result.waveform,
                tuningHz = result.tuningHz,
                silenceStartMs = result.silenceAtStartMs,
                silenceEndMs = silenceEndMs,
                analyzedAt = System.currentTimeMillis(),
                analysisVersion = AudioAnalysisEngine.CURRENT_ANALYSIS_VERSION
            )
        )
        val now = System.currentTimeMillis()
        val values = linkedMapOf<String, com.theveloper.pixelplay.data.metadata.MetadataClaim>()
        fun record(key: String, value: Any?, unit: String? = null, estimated: Boolean = true) {
            if (value == null) return
            values[key] = com.theveloper.pixelplay.data.metadata.MetadataClaim(value.toString(), "local-dsp", now,
                state = if (estimated) com.theveloper.pixelplay.data.metadata.MetadataState.ESTIMATED else com.theveloper.pixelplay.data.metadata.MetadataState.KNOWN,
                unit = unit, methodVersion = AudioAnalysisEngine.CURRENT_ANALYSIS_VERSION.toString(), assetRevision = fileProbe.sha256Checksum)
        }
        record("quality.audio_hash", fileProbe.sha256Checksum, estimated = false)
        record("timing.coverage_start", 0, "ms", false)
        record("timing.coverage_end", analysedDurationMs, "ms", false)
        record("timing.sample_rate", pcm.sampleRate, "Hz", false)
        record("timing.bpm", result.bpm, "BPM")
        record("timing.beats", beatGridJson, "seconds")
        record("harmony.key_candidates", result.musicKey)
        record("harmony.camelot", result.camelot)
        record("quality.rms", result.loudnessDb, "dBFS", false)
        record("quality.integrated_lufs", lufsIntegrated, "LUFS")
        record("structure.start_silence", result.silenceAtStartMs, "ms")
        record("structure.end_silence", silenceEndMs, "ms")
        record("quality.analyser_version", AudioAnalysisEngine.CURRENT_ANALYSIS_VERSION, estimated = false)
        record("quality.source_limitations", if (coversWholeAsset) "Decoded mono analysis; stereo and true-peak measurements need the original channels." else if (tail != null) "The first $analysedDurationMs ms and the last $TAIL_WINDOW_SECONDS s were analysed; integrated loudness is estimated from those windows." else "Only the first $analysedDurationMs ms were analysed. Whole-track loudness and outro timing remain unknown.", estimated = false)
        metadataStore.record(song.toSong(), values)
        return true
    }

    /** End-of-track descriptors for tracks longer than the head decode window. */
    private class TailAnalysis(
        /** Trailing silence (ms, -60 dBFS); null when not trustworthy. */
        val silenceEndMs: Long?,
        /**
         * Integrated loudness gated over head + tail (overlap removed); null when
         * together they cover less than [MIN_LOUDNESS_COVERAGE] of the track.
         */
        val lufsIntegrated: Float?,
    )

    private suspend fun analyseTail(uri: Uri, head: PcmAudio, trackDurationMs: Long): TailAnalysis? {
        val tail = runCatching { PcmDecoder.decodeTail(context, uri, TAIL_WINDOW_SECONDS) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull() ?: return null
        val tailMs = (tail.durationSeconds * 1000).toLong()
        val budgetMs = (TAIL_WINDOW_SECONDS + PcmDecoder.TAIL_SLACK_SECONDS) * 1000L
        // Hitting the decode budget means end-of-stream may not have been reached,
        // so nothing measured "from the end" can be trusted.
        if (tailMs <= 0L || tailMs >= budgetMs - 500L) return null

        return withContext(Dispatchers.Default) {
            val silence = SilenceDetector.detect(tail.samples, tail.sampleRate)
            val silenceEndMs = silence.silenceAtEndMs.takeIf { it < tailMs - 100L }

            val lufs = if (trackDurationMs > 0L && tail.sampleRate == head.sampleRate) {
                val headMs = (head.durationSeconds * 1000).toLong()
                val tailStartMs = (trackDurationMs - tailMs).coerceAtLeast(0L)
                val overlapMs = (headMs - tailStartMs).coerceIn(0L, tailMs)
                val coverage = (headMs + tailMs - overlapMs).toDouble() / trackDurationMs
                if (coverage >= MIN_LOUDNESS_COVERAGE) {
                    val skip = (overlapMs * tail.sampleRate / 1000L).toInt().coerceIn(0, tail.samples.size)
                    // Head + tail measured as one signal without building the concatenation
                    // (that copy plus the filter copies was ~70 MB for a long track).
                    EnergyLoudness.lufsIntegrated(head.sampleRate, head.samples, tail.samples, skip)
                        .takeIf { it > EnergyLoudness.DB_FLOOR }
                } else {
                    null
                }
            } else {
                null
            }
            TailAnalysis(silenceEndMs = silenceEndMs, lufsIntegrated = lufs)
        }
    }

    /**
     * Picks the local/decodable Uri for a song. Streams (http/https) are not
     * analyzable and return null; that is a skip, not an error. Exotic schemes
     * are attempted and simply fail to decode.
     */
    private fun resolvePlayableUri(song: SongEntity): Uri? {
        val uri = Uri.parse(song.contentUriString)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> null
            null -> {
                val file = File(song.filePath)
                if (file.exists()) Uri.fromFile(file) else null
            }
            else -> uri
        }
    }

    // ─── Stage 3: lyrics ─────────────────────────────────────────────────

    private suspend fun runLyricsStage(song: SongEntity): Boolean {
        val domainSong = song.toSong()
        // getStoredLyrics performs no network: covers songs.lyrics, the lyrics
        // table and the JSON cache.
        val stored = runCatching { lyricsRepository.getStoredLyrics(domainSong) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull()
        if (stored != null && !stored.first.synced.isNullOrEmpty()) return true

        if (networkAccessPolicy.getDecision(NetworkPurpose.Lyrics) != NetworkDecision.Allowed) {
            return stored != null
        }

        if (stored != null) {
            // Plain lyrics only: look for a synced version (back-off aware), keep plain otherwise.
            runCatching { lyricsRepository.upgradeToSynced(domainSong) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            return true
        }
        // getLyrics persists successful LRCLIB results to the lyrics table
        // (source "remote") itself, so nothing else to write here.
        val fetched = runCatching { lyricsRepository.getLyrics(domainSong) != null }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(false)
        if (fetched) {
            mergeAndStoreProvenance(
                song.id,
                listOf(MetadataProvenance(FIELD_LYRICS, ProvenanceSource.API, System.currentTimeMillis()))
            )
        }
        return fetched
    }

    // ─── Provenance ──────────────────────────────────────────────────────

    /**
     * Merges new entries into track_personalization.metadata_provenance_json:
     * existing entries for the same field are replaced by the newest write.
     */
    private suspend fun mergeAndStoreProvenance(songId: Long, newEntries: List<MetadataProvenance>) {
        runCatching {
            enrichmentDao.ensurePersonalizationRow(songId)
            val current = enrichmentDao.getPersonalization(songId) ?: return@runCatching
            val existing = current.metadataProvenanceJson
                ?.takeIf { it.isNotBlank() }
                ?.let { raw ->
                    runCatching { json.decodeFromString<List<MetadataProvenance>>(raw) }.getOrNull()
                }
                .orEmpty()
            val newFields = newEntries.map { it.field }.toSet()
            val merged = existing.filterNot { it.field in newFields } + newEntries
            enrichmentDao.upsertPersonalization(
                current.copy(metadataProvenanceJson = json.encodeToString(merged))
            )
        }.onFailure { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            Timber.tag(TAG).w(throwable, "provenance write failed for #%d", songId)
        }
    }

    // ─── Network gating ──────────────────────────────────────────────────

    /** True unless the user restricted enrichment to unmetered networks and we cannot confirm one. */
    private suspend fun wifiOnlySatisfied(): Boolean {
        if (!userPreferencesRepository.enrichmentWifiOnlyFlow.first()) return true
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        }.getOrDefault(false) // conservative: on failure assume metered
    }

    companion object {
        /** One decoded track in memory at a time, app-wide (worker + song-info sheet). */
        private val PCM_GATE = kotlinx.coroutines.sync.Semaphore(1)

        /** Heap that must be free before a decode starts (decode + analysers peak ~60 MB). */
        private const val PCM_MIN_HEADROOM_BYTES = 72L * 1024 * 1024
        private const val TAG = "EnrichmentRepository"

        /** Seconds decoded from the end of a track for end-of-track descriptors. */
        private const val TAIL_WINDOW_SECONDS = 75

        /** Minimum share of a track head + tail must cover to estimate its loudness. */
        private const val MIN_LOUDNESS_COVERAGE = 0.6

        const val PROVIDER_MB_RECORDING = "musicbrainz_recording"
        const val PROVIDER_MB_RELEASE = "musicbrainz_release"
        const val PROVIDER_ISRC = "isrc"
        const val ARTWORK_SOURCE_CAA = "cover_art_archive"

        private const val FIELD_GENRE = "genre"
        private const val FIELD_GENRES = "genres"
        private const val FIELD_TAGS = "tags"
        private const val FIELD_RELEASE_DATE = "release_date"
        private const val FIELD_RECORD_LABEL = "record_label"
        private const val FIELD_COUNTRY = "country"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_ARTWORK = "artwork"
        private const val FIELD_LYRICS = "lyrics"
    }
}

