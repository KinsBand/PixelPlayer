package com.theveloper.pixelplay.data.ai.gemma

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.theveloper.pixelplay.data.artist.ArtistHubRepository
import com.theveloper.pixelplay.data.artist.BandMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * The artist page's "Ask about…" tools for Gemma 4 (function calling through LiteRT-LM). Three
 * focused, keyless tools that return short structured results instead of raw web pages, so a
 * phone-sized model stays within memory and doesn't garble data:
 *
 * - [getBandRosterAndPfps]: members, roles and current/past from MusicBrainz, with headshots from
 *   Wikipedia (Deezer as a fallback). The pictures go straight to the screen ([onRoster]); the
 *   model only gets names and roles (it never has to repeat image URLs).
 * - [searchArtistWebInfo]: Wikipedia search, title + snippet + link per result.
 * - [readWebpageMarkdown]: one page as clean text (Jina Reader), cut to fit.
 *
 * Tool calls arrive on LiteRT-LM's worker thread (inside a blocking request), so the network
 * calls run with [runBlocking] on the IO dispatcher. All of it goes through NetworkAccessPolicy.
 */
class ArtistAgentTools(
    private val hub: ArtistHubRepository,
    private val onRoster: (List<BandMember>) -> Unit,
) : ToolSet {

    @Tool(description = "Gets the members of a band or group: each member's name, instruments or role, and whether they are a current or former member. Shows their profile pictures on screen. Use it whenever the user asks who is in a band or about a specific player.")
    fun getBandRosterAndPfps(
        @ToolParam(description = "The exact name of the band or group, e.g. 'The Cure'.") bandName: String,
        @ToolParam(description = "True to include former / original members; false for the current lineup only.") includeFormerMembers: Boolean = false,
    ): Map<String, Any> {
        val members = runBlocking(Dispatchers.IO) { runCatching { hub.rosterFor(bandName) }.getOrDefault(emptyList()) }
        val shown = if (includeFormerMembers) members else members.filter { it.isCurrent }.ifEmpty { members }
        if (shown.isNotEmpty()) onRoster(members)
        return mapOf(
            "band" to bandName,
            "found" to shown.isNotEmpty(),
            "members" to shown.map { m ->
                mapOf(
                    "name" to m.name,
                    "role" to m.role.ifBlank { "member" },
                    "status" to if (m.isCurrent) "current" else "former",
                    "photo_shown" to (m.imageUrl != null),
                )
            },
        )
    }

    @Tool(description = "Searches the web (Wikipedia) for facts about an artist or band member: biography, albums, gear, song meanings or news. Returns titles, short snippets and links.")
    fun searchArtistWebInfo(
        @ToolParam(description = "A short, focused search query, e.g. 'J Mascis guitar rig'.") query: String,
    ): Map<String, Any> {
        val results = runBlocking(Dispatchers.IO) { runCatching { hub.webSearch(query) }.getOrDefault(emptyList()) }
        return mapOf(
            "query" to query,
            "results" to results.map { (title, snippet, url) -> mapOf("title" to title, "snippet" to snippet, "url" to url) },
        )
    }

    @Tool(description = "Reads the full text of a web page (an https link from searchArtistWebInfo) when the snippets aren't enough.")
    fun readWebpageMarkdown(
        @ToolParam(description = "The full https URL to read.") url: String,
    ): Map<String, Any> {
        val text = runBlocking(Dispatchers.IO) { runCatching { hub.readPage(url) }.getOrNull() }
        return mapOf("url" to url, "text" to (text ?: "Could not read this page."))
    }
}
