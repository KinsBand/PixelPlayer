package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.parseJsonObject
import com.theveloper.pixelplay.data.database.serializeJsonObject
import com.theveloper.pixelplay.data.model.AudioTech
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

/** One entry in the "Versions" list of the audio details sheet. */
@Immutable
data class SongVersion(
    val song: Song,
    /** Version tag pulled from the title, e.g. "Live", "Remastered 2011". Null for untagged titles. */
    val tag: String?,
    val isOriginal: Boolean
)

/** Audio values read straight from the file by an on-demand scan. Null = still not found. */
@Immutable
data class ScannedAudio(
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val bitDepth: Int?
)

sealed interface AudioScanState {
    data object Idle : AudioScanState
    data object Scanning : AudioScanState
    @Immutable
    data class Done(val result: ScannedAudio) : AudioScanState
}

sealed interface SongVersionsState {
    data object Loading : SongVersionsState
    @Immutable
    data class Loaded(val versions: List<SongVersion>) : SongVersionsState
}

/** Versions of the song found online (studio, live, acoustic, remixes, covers…). */
sealed interface OnlineVersionsState {
    data object Idle : OnlineVersionsState
    data object Loading : OnlineVersionsState
    @Immutable
    data class Loaded(val versions: List<SongVersion>) : OnlineVersionsState
    data object Failed : OnlineVersionsState
}

/**
 * Finds other versions of a song in the local library: same primary artist, same base title
 * (title with version tags like "(Live)" or "- Remastered 2011" stripped). The original is
 * listed first. On request it also searches online for every version of the song.
 */
@HiltViewModel
class AudioDetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val musicDao: MusicDao,
    private val youTubeRepository: YouTubeRepository
) : ViewModel() {

    private val _onlineVersions = MutableStateFlow<OnlineVersionsState>(OnlineVersionsState.Idle)
    val onlineVersions: StateFlow<OnlineVersionsState> = _onlineVersions.asStateFlow()
    private var onlineSongId: String? = null
    private var onlineJob: Job? = null

    private val _scanState = MutableStateFlow<AudioScanState>(AudioScanState.Idle)
    val scanState: StateFlow<AudioScanState> = _scanState.asStateFlow()
    private var scanJob: Job? = null

    private val _versions = MutableStateFlow<SongVersionsState>(SongVersionsState.Loading)
    val versions: StateFlow<SongVersionsState> = _versions.asStateFlow()

    private var loadedSongId: String? = null
    private var loadJob: Job? = null

    fun load(song: Song) {
        if (loadedSongId == song.id && _versions.value is SongVersionsState.Loaded) return
        loadedSongId = song.id
        if (onlineSongId != song.id) {
            onlineJob?.cancel()
            onlineSongId = null
            _onlineVersions.value = OnlineVersionsState.Idle
        }
        scanJob?.cancel()
        _scanState.value = AudioScanState.Idle
        loadJob?.cancel()
        _versions.value = SongVersionsState.Loading
        loadJob = viewModelScope.launch {
            val result = try {
                findVersions(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listOf(SongVersion(song, SongVersionMatcher.versionTag(song.title), isOriginal = true))
            }
            _versions.value = SongVersionsState.Loaded(result)
        }
    }

    /**
     * Searches online for every version of [song]: the plain title plus live, acoustic, remix
     * and cover queries, merged and filtered to results whose base title matches. Songs already
     * in the local list are left out. Runs once per song; [force] retries after a failure.
     */
    fun loadOnline(song: Song, force: Boolean = false) {
        val state = _onlineVersions.value
        if (!force && onlineSongId == song.id &&
            (state is OnlineVersionsState.Loading || state is OnlineVersionsState.Loaded)
        ) return
        onlineSongId = song.id
        onlineJob?.cancel()
        _onlineVersions.value = OnlineVersionsState.Loading
        onlineJob = viewModelScope.launch {
            _onlineVersions.value = try {
                OnlineVersionsState.Loaded(findOnlineVersions(song))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Online version search failed for ${song.id}: ${e.message}")
                OnlineVersionsState.Failed
            }
        }
    }

    private suspend fun findOnlineVersions(song: Song): List<SongVersion> {
        val base = SongVersionMatcher.baseTitle(song.title)
        if (base.isBlank()) return emptyList()
        val artist = SongVersionMatcher.primaryArtist(song.artist)
        val queries = listOf(
            "$base $artist",
            "$base $artist live",
            "$base $artist acoustic",
            "$base $artist remix",
            "$base cover"
        ).map { it.replace(Regex("""\s+"""), " ").trim() }.distinct()

        val results = coroutineScope {
            queries.map { query ->
                async {
                    try {
                        withTimeoutOrNull(ONLINE_QUERY_TIMEOUT_MS) { youTubeRepository.searchSongs(query) }.orEmpty()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Online query '$query' failed: ${e.message}")
                        null
                    }
                }
            }.awaitAll()
        }
        if (results.all { it == null }) error("All online version queries failed")

        val localIds = (_versions.value as? SongVersionsState.Loaded)
            ?.versions?.mapNotNull { it.song.youtubeId ?: it.song.id }?.toSet().orEmpty()
        return withContext(Dispatchers.Default) {
            SongVersionMatcher.orderOnlineVersions(
                current = song,
                candidates = results.filterNotNull().flatten(),
                excludeIds = localIds + setOfNotNull(song.id, song.youtubeId)
            )
        }
    }

    /**
     * Reads sample rate, bitrate, format and bit depth directly from this song's file and saves
     * whatever it finds to the library, so the values stick after the sheet closes.
     */
    fun scanSong(song: Song) {
        if (_scanState.value == AudioScanState.Scanning) return
        _scanState.value = AudioScanState.Scanning
        scanJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { readAudioFromFile(song) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Scan failed for ${song.id}: ${e.message}")
                ScannedAudio(null, null, null, null)
            }
            persist(song, result)
            _scanState.value = AudioScanState.Done(result)
        }
    }

    private fun readAudioFromFile(song: Song): ScannedAudio {
        var mimeType: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var bitDepth: Int? = null

        val file = song.path.takeIf { it.isNotBlank() }?.let(::File)
        val useFilePath = file != null && file.exists() && file.canRead()
        val contentUri = song.contentUriString.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (!useFilePath && contentUri == null) return ScannedAudio(null, null, null, null)

        val retriever = MediaMetadataRetriever()
        try {
            if (useFilePath) retriever.setDataSource(file!!.path) else retriever.setDataSource(context, contentUri)
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Retriever failed for ${song.id}: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }

        // Fill anything still missing from the audio track's format.
        val extractor = MediaExtractor()
        try {
            if (useFilePath) extractor.setDataSource(file!!.path) else extractor.setDataSource(context, contentUri!!, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME)
                if (trackMime?.startsWith("audio/") != true) continue
                mimeType = mimeType ?: trackMime
                if (sampleRate == null && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (bitrate == null && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                if (bitDepth == null && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    bitDepth = when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                        android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                        android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                        android.media.AudioFormat.ENCODING_PCM_32BIT,
                        android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                        else -> null
                    }
                }
                break
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extractor failed for ${song.id}: ${e.message}")
        } finally {
            extractor.release()
        }

        return ScannedAudio(
            mimeType = mimeType?.takeIf { it.isNotBlank() },
            bitrate = bitrate?.takeIf { it > 0 },
            sampleRate = sampleRate?.takeIf { it > 0 },
            bitDepth = bitDepth?.takeIf { it > 0 }
        )
    }

    private suspend fun persist(song: Song, result: ScannedAudio) {
        val songId = song.id.toLongOrNull() ?: return // cloud/streamed songs aren't in the songs table
        if (result == ScannedAudio(null, null, null, null)) return
        try {
            val audioTechJson = result.bitDepth?.let { depth ->
                val stored = musicDao.getSongByIdOnce(songId)
                    ?.audioTechJson
                    ?.let { parseJsonObject<AudioTech>(it) }
                    ?: song.audioTech
                serializeJsonObject(stored.copy(bitDepth = depth))
            }
            musicDao.updateScannedAudioMetadata(
                songId = songId,
                mimeType = result.mimeType,
                bitrate = result.bitrate,
                sampleRate = result.sampleRate,
                audioTechJson = audioTechJson
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save scanned metadata for ${song.id}: ${e.message}")
        }
    }

    private suspend fun findVersions(song: Song): List<SongVersion> {
        val baseTitle = SongVersionMatcher.baseTitle(song.title)
        val candidates = if (baseTitle.isBlank()) {
            emptyList()
        } else {
            musicRepository.searchSongs(baseTitle, titleOnly = true).first()
        }
        return withContext(Dispatchers.Default) {
            SongVersionMatcher.orderVersions(song, candidates)
        }
    }
}

private const val TAG = "AudioDetailsViewModel"
private const val ONLINE_QUERY_TIMEOUT_MS = 12_000L
private const val MAX_ONLINE_VERSIONS = 25

internal object SongVersionMatcher {
    private val versionKeywords = listOf(
        "live", "remaster", "acoustic", "remix", "mix", "edit", "version", "demo",
        "mono", "stereo", "deluxe", "instrumental", "unplugged", "radio", "extended",
        "reprise", "sped up", "slowed"
    )
    private val featKeywords = listOf("feat.", "feat ", "ft.", "ft ", "featuring", "with ")
    private val bracketGroup = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    private val dashSuffix = Regex("""\s+[-–—]\s+(.+)$""")
    private val whitespace = Regex("""\s+""")
    private val artistSplit = Regex("""\s*(,|&|;|/|\sx\s|\sfeat\.?\s|\sft\.?\s|\sfeaturing\s)\s*""", RegexOption.IGNORE_CASE)

    private fun String.hasVersionKeyword(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return versionKeywords.any { lower.contains(it) }
    }

    private fun String.isFeatureCredit(): Boolean {
        val lower = lowercase(Locale.ROOT).trimStart()
        return featKeywords.any { lower.startsWith(it) }
    }

    /** "Song (Live at Wembley)" -> "Live at Wembley"; "Song - 2011 Remaster" -> "2011 Remaster". */
    fun versionTag(title: String): String? {
        val tags = mutableListOf<String>()
        bracketGroup.findAll(title).forEach { match ->
            val inner = match.groupValues[1].trim()
            if (inner.hasVersionKeyword()) tags += inner
        }
        val withoutBrackets = bracketGroup.replace(title, "")
        dashSuffix.find(withoutBrackets)?.let { match ->
            val suffix = match.groupValues[1].trim()
            if (suffix.hasVersionKeyword()) tags += suffix
        }
        return tags.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Lower-cased title with version tags and feature credits removed. */
    fun baseTitle(title: String): String {
        var result = bracketGroup.replace(title) { match ->
            val inner = match.groupValues[1]
            if (inner.hasVersionKeyword() || inner.isFeatureCredit()) "" else match.value
        }
        dashSuffix.find(result)?.let { match ->
            if (match.groupValues[1].hasVersionKeyword()) result = result.removeRange(match.range)
        }
        return result.lowercase(Locale.ROOT).replace(whitespace, " ").trim()
    }

    fun primaryArtist(artist: String): String =
        artist.split(artistSplit).firstOrNull().orEmpty().lowercase(Locale.ROOT).trim()

    private fun sameArtist(a: Song, b: Song): Boolean {
        if (a.artistId != 0L && a.artistId != -1L && a.artistId == b.artistId) return true
        val artistA = primaryArtist(a.artist)
        return artistA.isNotEmpty() && artistA == primaryArtist(b.artist)
    }

    private fun Song.yearOrMax(): Int = year.takeIf { it > 0 } ?: Int.MAX_VALUE

    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    private fun String.squash(): String = lowercase(Locale.ROOT).replace(nonWord, " ").trim()

    private val fillerWords = setOf(
        "official", "video", "audio", "lyrics", "lyric", "hd", "hq", "4k", "music", "visualizer",
        "visualiser", "performance", "session", "sessions", "version", "feat", "ft", "featuring",
        "with", "and", "x", "the", "by", "from", "at", "in", "on", "topic", "vevo", "full", "song"
    )

    /**
     * True when [candidate]'s title is [base] with only version/feature tags, artist names or
     * filler ("Official Video") around it, so "Love" doesn't match "I Love You".
     */
    fun titleMatchesBase(candidate: String, base: String, artists: String = ""): Boolean {
        val baseWords = base.squash().split(' ').filter { it.isNotEmpty() }
        if (baseWords.isEmpty()) return false
        val candidateWords = baseTitle(candidate).squash().split(' ').filter { it.isNotEmpty() }
        val start = (0..candidateWords.size - baseWords.size).firstOrNull { i ->
            candidateWords.subList(i, i + baseWords.size) == baseWords
        } ?: return false
        val leftover = candidateWords.subList(0, start) + candidateWords.subList(start + baseWords.size, candidateWords.size)
        if (leftover.isEmpty()) return true
        val allowed = artists.squash().split(' ').toSet()
        return leftover.all { word ->
            word in allowed || word in fillerWords || word.all(Char::isDigit) ||
                versionKeywords.any { keyword -> keyword.split(' ').any { word.startsWith(it) } }
        }
    }

    /**
     * Online results that are versions of [current]: same base title, not already listed.
     * The same artist's versions come first (untagged studio versions before tagged ones),
     * then other artists' covers.
     */
    fun orderOnlineVersions(current: Song, candidates: List<Song>, excludeIds: Set<String>): List<SongVersion> {
        val base = baseTitle(current.title)
        val currentArtist = primaryArtist(current.artist)
        return candidates
            .asSequence()
            .filter { it.id !in excludeIds && (it.youtubeId == null || it.youtubeId !in excludeIds) }
            .distinctBy { it.youtubeId ?: it.id }
            .filter { titleMatchesBase(it.title, base, "${current.artist} ${it.artist}") }
            .map { candidate ->
                val tag = versionTag(candidate.title)
                val sameArtist = currentArtist.isNotEmpty() &&
                    (primaryArtist(candidate.artist) == currentArtist ||
                        candidate.artist.lowercase(Locale.ROOT).contains(currentArtist))
                Triple(candidate, tag, sameArtist)
            }
            .sortedWith(
                compareByDescending<Triple<Song, String?, Boolean>> { it.third }
                    .thenBy { it.second != null }
            )
            .take(MAX_ONLINE_VERSIONS)
            .map { (song, tag, sameArtist) ->
                SongVersion(song, tag ?: if (sameArtist) null else "Cover", isOriginal = false)
            }
            .toList()
    }

    fun orderVersions(current: Song, candidates: List<Song>): List<SongVersion> {
        val base = baseTitle(current.title)
        val matches = (candidates + current)
            .distinctBy { it.id }
            .filter { it.id == current.id || (baseTitle(it.title) == base && sameArtist(current, it)) }
            .map { it to versionTag(it.title) }

        val original = matches
            .filter { it.second == null }
            .minWithOrNull(compareBy({ it.first.yearOrMax() }, { it.first.title }))
            ?: matches.minWithOrNull(compareBy({ it.first.yearOrMax() }, { it.first.title }))
            ?: (current to versionTag(current.title))

        val others = matches
            .filter { it.first.id != original.first.id }
            .sortedWith(compareBy({ it.first.yearOrMax() }, { it.first.title.lowercase(Locale.ROOT) }))

        return buildList {
            add(SongVersion(original.first, original.second, isOriginal = true))
            others.forEach { (song, tag) -> add(SongVersion(song, tag, isOriginal = false)) }
        }
    }
}
