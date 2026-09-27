package com.theveloper.pixelplay.data.network.musicbrainz

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for the Cover Art Archive API.
 * Base URL: https://coverartarchive.org/
 *
 * Only the JSON manifest is fetched here; the actual image bytes are
 * downloaded by the repository via plain OkHttp from the URLs in the
 * manifest (CAA redirects to archive.org, which OkHttp follows).
 *
 * Shares MusicBrainz's rate policy (both are MetaBrainz services):
 * covered by MusicBrainzRateLimiter.
 */
interface CoverArtArchiveApiService {

    /**
     * Get the artwork manifest for a release. Responds 404 when the
     * release has no artwork — callers should treat that as "no art".
     */
    @GET("release/{mbid}")
    suspend fun getReleaseArtwork(@Path("mbid") mbid: String): CaaReleaseResponse
}
