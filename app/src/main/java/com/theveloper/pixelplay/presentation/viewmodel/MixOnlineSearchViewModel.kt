package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import com.theveloper.pixelplay.presentation.components.MixFilterField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** An online choice in a Daily Mix filter picker (or the Artist tab search). */
@Immutable
data class OnlineFilterSuggestion(
    val value: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
)

/**
 * Online look-ups for the Daily Mix sheet: suggestions while typing in a filter (Artist and
 * Album from Deezer, Genre from a fixed list), and songs for a filter value that isn't in the
 * library (so a typed artist/genre/mood still makes a mix). Everything is cached for the
 * session and skipped while offline.
 */
@HiltViewModel
class MixOnlineSearchViewModel @Inject constructor(
    private val deezer: DeezerApiService,
    private val youTube: YouTubeRepository,
    private val connectivity: ConnectivityStateHolder,
) : ViewModel() {
    private val suggestionCache = HashMap<String, List<OnlineFilterSuggestion>>()
    private val songCache = HashMap<String, List<Song>>()

    val isOnline get() = connectivity.isOnline

    /** Whether typing in [field]'s picker looks anything up online. */
    fun searchesOnline(field: MixFilterField): Boolean =
        field == MixFilterField.Artist || field == MixFilterField.Album || field == MixFilterField.Genre

    suspend fun suggest(field: MixFilterField, query: String): List<OnlineFilterSuggestion> {
        val q = query.trim()
        if (q.length < MIN_QUERY || !searchesOnline(field)) return emptyList()
        if (field == MixFilterField.Genre) {
            val n = normalizeMixQuery(q)
            return KnownGenres.filter { normalizeMixQuery(it).contains(n) }
                .sortedBy { if (normalizeMixQuery(it).startsWith(n)) 0 else 1 }
                .take(8)
                .map { OnlineFilterSuggestion(it) }
        }
        if (!connectivity.isOnline.value) return emptyList()
        val key = "${field.name}|${normalizeMixQuery(q)}"
        synchronized(suggestionCache) { suggestionCache[key] }?.let { return it }
        val result = try {
            withTimeoutOrNull(6_000) {
                when (field) {
                    MixFilterField.Artist -> deezer.searchArtist(q, limit = 8).data.map {
                        OnlineFilterSuggestion(it.name, imageUrl = it.pictureMedium ?: it.picture)
                    }
                    MixFilterField.Album -> deezer.searchAlbum(q, limit = 8).data.map {
                        OnlineFilterSuggestion(it.title, subtitle = it.artist?.name, imageUrl = it.coverMedium)
                    }
                    else -> emptyList()
                }
            }.orEmpty().distinctBy { normalizeMixQuery(it.value) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        synchronized(suggestionCache) { suggestionCache[key] = result }
        return result
    }

    /**
     * Songs for [value] in [field] found online: the artist's songs, the album's songs, or a
     * search for "<value> songs" (genre / mood / activity / anything typed).
     */
    suspend fun songsForFilter(field: MixFilterField, value: String, limit: Int = 24): List<Song> {
        if (!connectivity.isOnline.value) return emptyList()
        val key = "${field.name}|${normalizeMixQuery(value)}"
        synchronized(songCache) { songCache[key] }?.let { return it }
        val query = when (field) {
            MixFilterField.Artist -> value
            MixFilterField.Album -> "$value album"
            MixFilterField.Era -> "$value hits"
            else -> "$value songs"
        }
        val songs = try {
            withTimeoutOrNull(8_000) { youTube.searchSongs(query) }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val n = normalizeMixQuery(value)
        val filtered = when (field) {
            // Keep the artist's own songs when the search mixes in others.
            MixFilterField.Artist -> songs.filter { normalizeMixQuery(it.displayArtist).contains(n) }.ifEmpty { songs }
            MixFilterField.Album -> songs.filter { normalizeMixQuery(it.album).contains(n) }.ifEmpty { songs }
            else -> songs
        }.distinctBy { it.id }.take(limit)
        if (filtered.isNotEmpty()) synchronized(songCache) { songCache[key] = filtered }
        return filtered
    }

    private companion object {
        const val MIN_QUERY = 2
    }
}

/** Lowercase, accents stripped, spaces collapsed: for matching and de-duplicating names. */
fun normalizeMixQuery(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

/** Local options first, then online ones not already listed (case- and accent-insensitive). */
fun mergeLocalAndOnline(local: List<String>, online: List<OnlineFilterSuggestion>): List<OnlineFilterSuggestion> {
    val seen = local.mapTo(HashSet()) { normalizeMixQuery(it) }
    return online.filter { seen.add(normalizeMixQuery(it.value)) }
}

/** Whether the picker offers "Use “typed text”": custom fields, text typed, no exact match. */
fun shouldOfferCustom(allowsCustom: Boolean, typed: String, options: List<String>): Boolean {
    val t = typed.trim()
    if (!allowsCustom || t.isEmpty()) return false
    val n = normalizeMixQuery(t)
    return options.none { normalizeMixQuery(it) == n }
}

/** Genres offered while typing (no network call per keystroke). */
val KnownGenres = listOf(
    "Pop", "Rock", "Hip-Hop", "Rap", "R&B", "Soul", "Funk", "Disco", "Jazz", "Blues", "Classical",
    "Electronic", "House", "Deep House", "Techno", "Trance", "Drum and Bass", "Dubstep", "Ambient",
    "Lo-fi", "Indie", "Indie Pop", "Indie Rock", "Alternative", "Punk", "Pop Punk", "Emo", "Metal",
    "Heavy Metal", "Hard Rock", "Grunge", "Folk", "Country", "Bluegrass", "Reggae", "Dancehall",
    "Afrobeats", "Amapiano", "Latin", "Reggaeton", "Salsa", "Bossa Nova", "K-Pop", "J-Pop",
    "City Pop", "Gospel", "Soundtrack", "Synthwave", "Shoegaze", "Dream Pop", "Trap", "Drill",
    "Grime", "UK Garage", "Hyperpop", "Singer-Songwriter", "Acoustic", "Chillout", "Downtempo",
)
