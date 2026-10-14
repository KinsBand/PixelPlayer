package com.theveloper.pixelplay.data.spotify.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the web-player chunk discovery: artist-page / search operations live in lazily loaded
 * chunks, not the main script (that's why the artist page's Spotify parts never loaded).
 * The main-bundle snippet is hand-written in webpack's shape, not a captured file.
 */
class SpotifyWebBundleChunksTest {
    private val main = """
        x.u=e=>""+({12:"xpui-routes-artist",34:"xpui-routes-search",56:"vendors~ui"}[e]||e)+"."+{12:"a1b2c3d4",34:"0f0f0f0f",56:"deadbeef"}[e]+".js";
        y.miniCssF=e=>"css/"+{12:"9999aaaa"}[e]+".css";
    """.trimIndent()

    @Test fun `pairs chunk names with their hashes`() {
        val files = SpotifyWebBundle.chunkFiles(main)
        assertTrue("xpui-routes-artist.a1b2c3d4.js" in files, files.toString())
        assertTrue("xpui-routes-search.0f0f0f0f.js" in files, files.toString())
        assertTrue("vendors~ui.deadbeef.js" in files, files.toString())
    }

    @Test fun `indexes every operation in a chunk`() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        val chunk = """new o.l("queryArtistOverview","query","$hashA",null);new o.l("addToPlaylist","mutation","$hashB",null)"""
        val ops = SpotifyWebBundle.indexOperations(chunk)
        assertEquals(hashA, ops["queryArtistOverview"])
        assertEquals(hashB, ops["addToPlaylist"])
    }
}
