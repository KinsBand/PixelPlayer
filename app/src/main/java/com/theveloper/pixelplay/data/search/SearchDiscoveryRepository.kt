package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.data.network.lastfm.LastFmRepository
import com.theveloper.pixelplay.data.repository.ArtistAggregationRepository
import com.theveloper.pixelplay.data.repository.ArtistCatalog
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds Search's "Because you listened" cards: for an artist you play, artists that sound
 * like them (Deezer related, plus Last.fm similar when a Last.fm key is set) and searches for
 * their mood / genre / era, read from your copies of their songs (and Last.fm tags).
 *
 * What's fetched per artist is kept for [INPUT_TTL_MS]; the cards themselves reshuffle every
 * rotation ([SearchDiscovery.ROTATION_PERIOD_MS]) from those inputs, without network.
 */
@Singleton
class SearchDiscoveryRepository @Inject constructor(
    private val artistAggregation: ArtistAggregationRepository,
    private val musicRepository: MusicRepository,
    private val lastFm: LastFmRepository
) {
    private class Inputs(
        val moodQueries: List<String>,
        val similar: List<SimilarArtist>,
        val fetchedAt: Long,
        /** False when built without network (similar artists may be missing). */
        val complete: Boolean
    )

    /** Artist id -> inputs. Only your current top artists are kept (pruned on every call). */
    private val inputs = ConcurrentHashMap<Long, Inputs>()

    /**
     * Cards for [anchors]. [exclude] = artists not to recommend (your top artists, so the
     * suggestions are new to you). With [allowNetwork] false only cached or local data is used,
     * for an instant first paint; returns null then if an anchor has nothing cached yet.
     */
    suspend fun hubs(
        anchors: List<LibraryInsightsRepository.ArtistCard>,
        keepIds: Set<Long>,
        exclude: Set<String>,
        slot: Long,
        allowNetwork: Boolean
    ): List<ArtistSearchSuggestionHub>? = coroutineScope {
        inputs.keys.retainAll(keepIds)
        val total = if (anchors.size == 1) 4 else 3
        val perArtist = anchors.map { artist ->
            async {
                val known = inputs[artist.id]
                val fresh = known != null && known.complete &&
                    System.currentTimeMillis() - known.fetchedAt < INPUT_TTL_MS
                val use = when {
                    fresh -> known
                    allowNetwork -> load(artist, known)
                    else -> known
                } ?: return@async null
                ArtistSearchSuggestionHub(
                    artist = artist,
                    suggestions = SearchDiscovery.buildSuggestions(
                        artistName = artist.name,
                        artistId = artist.id,
                        similar = use.similar,
                        moodQueries = use.moodQueries,
                        slot = slot,
                        total = total,
                        exclude = exclude
                    )
                )
            }
        }.awaitAll()
        if (perArtist.any { it == null }) null else perArtist.filterNotNull().filter { it.suggestions.isNotEmpty() }
    }

    private suspend fun load(artist: LibraryInsightsRepository.ArtistCard, previous: Inputs?): Inputs = withContext(Dispatchers.IO) {
        val songs = try {
            withTimeoutOrNull(LOCAL_TIMEOUT_MS) { musicRepository.getSongsForArtist(artist.id).first() }
                .orEmpty().take(MAX_SONGS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Discovery: couldn't read songs for an artist")
            emptyList()
        }
        // Last.fm returns null without a key (no request is made then).
        val lastFmInfo = try {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) { lastFm.getArtistInfo(artist.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val related = try {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) { artistAggregation.relatedArtists(artist.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val similar = buildList {
            related.orEmpty().forEach { add(SimilarArtist(it.name, it.imageUrl)) }
            lastFmInfo?.similarArtists.orEmpty().forEach { add(SimilarArtist(it)) }
        }.distinctBy { ArtistCatalog.fold(it.name) }
            .ifEmpty { previous?.similar.orEmpty() }
        val profile = SearchDiscovery.soundProfile(songs, lastFmInfo?.tags.orEmpty(), artist.name)
        val moods = SearchDiscovery.moodQueries(profile, artist.name).ifEmpty { previous?.moodQueries.orEmpty() }
        Inputs(
            moodQueries = moods,
            similar = similar,
            fetchedAt = System.currentTimeMillis(),
            // Nothing similar came back (offline, Deezer down): try again next time.
            complete = related != null && similar.isNotEmpty()
        ).also { inputs[artist.id] = it }
    }

    private companion object {
        /** Re-fetch an artist's similar artists and tags after this long. */
        const val INPUT_TTL_MS = 12L * 60 * 60 * 1000
        const val MAX_SONGS = 400
        const val LOCAL_TIMEOUT_MS = 3_000L
        const val NETWORK_TIMEOUT_MS = 8_000L
    }
}
