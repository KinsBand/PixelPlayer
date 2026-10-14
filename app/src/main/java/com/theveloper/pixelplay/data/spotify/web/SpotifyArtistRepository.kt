package com.theveloper.pixelplay.data.spotify.web

import android.content.Context
import com.google.gson.Gson
import com.theveloper.pixelplay.data.repository.ArtistCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The artist page's Spotify extras (playlists, concerts, merch, related artists) from the web
 * player: find the artist by exact name, then read their page. Needs the Spotify web sign-in
 * ([isAvailable]); otherwise, or when there's no confident match, [extras] returns null and the
 * page leaves those parts out. One lookup per artist per [TTL_MS] (memory + disk cache).
 */
@Singleton
class SpotifyArtistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pathfinder: SpotifyPathfinder,
    private val session: SpotifyWebSession,
) {
    private val gson = Gson()
    private val memory = android.util.LruCache<String, SpotifyArtistExtras>(30)
    // v2: results saved before the parser rewrite are ignored.
    private val dir: File get() = File(context.cacheDir, "spotify_artist_v2").apply { mkdirs() }

    val isAvailable: Boolean get() = session.hasSession

    suspend fun extras(artistName: String, force: Boolean = false): SpotifyArtistExtras? = withContext(Dispatchers.IO) {
        val key = ArtistCatalog.fold(artistName)
        if (!isAvailable || key.isBlank()) return@withContext null
        val cached = memory.get(key) ?: readDisk(key)?.also { memory.put(key, it) }
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < TTL_MS) return@withContext cached
        try {
            val search = pathfinder.query(SpotifyQueries.SEARCH_ARTISTS, JSONObject()
                .put("searchTerm", artistName)
                .put("offset", 0)
                .put("limit", 5)
                .put("numberOfTopResults", 5)
                .put("includeAudiobooks", false)
                .put("includePreReleases", false)
                .put("includeAuthors", false))
            val artistId = SpotifyArtistParsers.artistIdFromSearch(search, artistName) ?: run {
                Timber.w("Spotify artist extras: no exact Spotify match for this artist name")
                return@withContext null
            }
            val page = pathfinder.query(SpotifyQueries.ARTIST_OVERVIEW, JSONObject()
                .put("uri", "spotify:artist:$artistId")
                .put("locale", "")
                .put("includePrerelease", true))
            val extras = SpotifyArtistParsers.extras(page, artistId, System.currentTimeMillis())
            Timber.d(
                "Spotify artist extras: %d playlists, %d concerts, %d merch, %d related",
                extras.playlists.size, extras.concerts.size, extras.merch.size, extras.relatedArtists.size
            )
            // Structure only (no values), to see where Spotify moved things if a part comes back empty.
            if (extras.playlists.isEmpty() || extras.relatedArtists.isEmpty()) {
                Timber.d("Spotify artist page shape: %s", SpotifyArtistParsers.describe(page))
            }
            memory.put(key, extras)
            writeDisk(key, extras)
            extras
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.w(e, "Spotify artist extras unavailable")
            cached
        }
    }

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private fun readDisk(key: String): SpotifyArtistExtras? = try {
        fileFor(key).takeIf { it.exists() }?.reader()?.use { gson.fromJson(it, SpotifyArtistExtras::class.java) }
    } catch (e: Exception) {
        null
    }

    private fun writeDisk(key: String, extras: SpotifyArtistExtras) {
        try {
            fileFor(key).writeText(gson.toJson(extras))
        } catch (e: Exception) {
            Timber.w(e, "Couldn't cache Spotify artist extras")
        }
    }

    companion object {
        const val TTL_MS = 12 * 60 * 60 * 1000L
    }
}
