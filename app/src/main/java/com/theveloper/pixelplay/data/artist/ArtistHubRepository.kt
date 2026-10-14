package com.theveloper.pixelplay.data.artist

import com.theveloper.pixelplay.data.network.NetworkAccessPolicy
import com.theveloper.pixelplay.data.network.NetworkDecision
import com.theveloper.pixelplay.data.network.NetworkPurpose
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import com.theveloper.pixelplay.data.network.musicbrainz.MbArtistDetails
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the artist page's Socials tab and About section need beyond the catalogue. */
data class ArtistHubInfo(
    val socials: ArtistSocialHub = ArtistSocialHub(),
    /** Current members first, then past ones. Empty for solo artists or when unknown. */
    val members: List<BandMember> = emptyList(),
    /** The artist's Wikipedia page (any language) from MusicBrainz, for the About bio. */
    val wikipediaUrl: String? = null,
    /** The artist's Wikidata item (e.g. Q1299), used to find the English Wikipedia page. */
    val wikidataId: String? = null,
)

/**
 * Builds [ArtistHubInfo] from MusicBrainz (keyless, 1 request/s via its rate limiter): the
 * artist's web links become the Socials hub (official homepage = the website button) and its
 * "member of band" relations become the band members. Member pictures come from Deezer's
 * artist search (keyless), a few at a time. Results are cached in memory per artist.
 *
 * Goes through [NetworkAccessPolicy] (Enrichment): offline mode or enrichment off = nothing.
 * Never throws; an unknown artist just has an empty hub.
 */
@Singleton
class ArtistHubRepository @Inject constructor(
    private val musicBrainz: MusicBrainzRepository,
    private val deezer: DeezerApiService,
    private val networkPolicy: NetworkAccessPolicy,
    private val okHttpClient: okhttp3.OkHttpClient,
) {
    private val bioCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 24
    }
    private val cache = object : LinkedHashMap<String, ArtistHubInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtistHubInfo>?) = size > 24
    }

    suspend fun hubFor(artistName: String): ArtistHubInfo = withContext(Dispatchers.IO) {
        val key = artistName.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return@withContext ArtistHubInfo()
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        if (networkPolicy.getDecision(NetworkPurpose.Enrichment) != NetworkDecision.Allowed) {
            return@withContext ArtistHubInfo()
        }
        val details = musicBrainz.lookupArtistByName(artistName) ?: return@withContext ArtistHubInfo()
        val info = ArtistHubInfo(
            socials = socialHubOf(details),
            members = withPictures(membersOf(details)),
            wikipediaUrl = urlOfType(details, "wikipedia"),
            wikidataId = urlOfType(details, "wikidata")?.substringAfterLast('/')?.takeIf { it.matches(Regex("Q\\d+")) },
        )
        synchronized(cache) { cache[key] = info }
        info
    }

    /**
     * A short bio from Wikipedia (keyless REST summary) for the About section when Last.fm has
     * none: the page MusicBrainz links (directly or through Wikidata), else a name search that
     * only accepts a music page. Null offline, with enrichment off, or when nothing fits.
     */
    suspend fun wikipediaBio(artistName: String, hub: ArtistHubInfo): String? = withContext(Dispatchers.IO) {
        val key = artistName.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return@withContext null
        synchronized(bioCache) { bioCache[key] }?.let { return@withContext it }
        if (networkPolicy.getDecision(NetworkPurpose.Enrichment) != NetworkDecision.Allowed) return@withContext null
        val bio = try {
            val linked = hub.wikipediaUrl?.let(::enWikiTitleFromUrl)?.let { summary(it.first, it.second) }
            linked
                ?: hub.wikidataId?.let { enTitleFromWikidata(it) }?.let { summary("en", it) }
                ?: searchMusicPage(artistName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d("No Wikipedia bio: ${e.javaClass.simpleName}")
            null
        }
        if (bio != null) synchronized(bioCache) { bioCache[key] = bio }
        bio
    }

    private fun getJson(url: String): com.google.gson.JsonObject? {
        val request = okhttp3.Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject }.getOrNull()
        }
    }

    /** "https://de.wikipedia.org/wiki/Foo" → ("de", "Foo"). */
    private fun enWikiTitleFromUrl(url: String): Pair<String, String>? {
        val m = Regex("https?://([a-z\\-]+)\\.(?:m\\.)?wikipedia\\.org/wiki/([^?#]+)").find(url) ?: return null
        return m.groupValues[1] to java.net.URLDecoder.decode(m.groupValues[2], "UTF-8")
    }

    private fun enTitleFromWikidata(id: String): String? =
        getJson("https://www.wikidata.org/wiki/Special:EntityData/$id.json")
            ?.getAsJsonObject("entities")?.getAsJsonObject(id)
            ?.getAsJsonObject("sitelinks")?.getAsJsonObject("enwiki")
            ?.get("title")?.asString

    /** Wikipedia's lead-paragraph summary; disambiguation pages and stubs are skipped. */
    private fun summary(lang: String, title: String): String? {
        val encoded = android.net.Uri.encode(title.replace(' ', '_'))
        val json = getJson("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded") ?: return null
        if (json.get("type")?.asString == "disambiguation") return null
        return json.get("extract")?.asString?.trim()?.takeIf { it.length >= MIN_BIO_CHARS }
    }

    private fun searchMusicPage(artistName: String): String? {
        val q = android.net.Uri.encode("$artistName music")
        val hits = getJson("https://en.wikipedia.org/w/api.php?action=query&list=search&srlimit=4&format=json&srsearch=$q")
            ?.getAsJsonObject("query")?.getAsJsonArray("search") ?: return null
        val wanted = fold(artistName)
        for (hit in hits) {
            val title = hit.asJsonObject.get("title")?.asString ?: continue
            if (!fold(title).startsWith(wanted)) continue
            val text = summary("en", title) ?: continue
            if (MUSIC_WORDS.any { text.contains(it, ignoreCase = true) }) return text
        }
        return null
    }

    private fun fold(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun urlOfType(details: MbArtistDetails, type: String): String? =
        details.relations.firstOrNull { it.type.equals(type, ignoreCase = true) }?.url?.resource?.takeIf { it.isNotBlank() }

    private suspend fun withPictures(members: List<BandMember>): List<BandMember> = coroutineScope {
        val shown = members.take(MAX_MEMBERS)
        // One Wikipedia request for every member's headshot; Deezer only for those it lacks.
        val wiki = try {
            wikipediaHeadshots(shown.map { it.name })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        val gate = Semaphore(MEMBER_PICTURE_CONCURRENCY)
        shown.map { member ->
            async {
                wiki[member.name]?.let { return@async member.copy(imageUrl = it) }
                gate.withPermit {
                    val picture = try {
                        deezer.searchArtist(member.name, limit = 1).data.firstOrNull()
                            ?.takeIf { it.name.equals(member.name, ignoreCase = true) }
                            ?.let { it.pictureMedium ?: it.picture }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.d("No picture for a band member: ${e.message}")
                        null
                    }
                    member.copy(imageUrl = picture)
                }
            }
        }.awaitAll()
    }

    /**
     * Headshots for several people in one Wikipedia request (pageimages, 600 px, redirects
     * followed). Name → image URL; people without a page or picture are left out.
     */
    private fun wikipediaHeadshots(names: List<String>): Map<String, String> {
        if (names.isEmpty()) return emptyMap()
        val titles = android.net.Uri.encode(names.joinToString("|"))
        val query = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&titles=$titles&prop=pageimages%7Cdescription&piprop=thumbnail" +
                "&pithumbsize=600&redirects=1&format=json&formatversion=2"
        )?.getAsJsonObject("query") ?: return emptyMap()
        val redirectedFrom = HashMap<String, String>()
        query.getAsJsonArray("redirects")?.forEach { r ->
            val o = r.asJsonObject
            val from = o.get("from")?.asString
            val to = o.get("to")?.asString
            if (from != null && to != null) redirectedFrom[to] = from
        }
        val normalizedFrom = HashMap<String, String>()
        query.getAsJsonArray("normalized")?.forEach { r ->
            val o = r.asJsonObject
            val from = o.get("from")?.asString
            val to = o.get("to")?.asString
            if (from != null && to != null) normalizedFrom[to] = from
        }
        val result = HashMap<String, String>()
        query.getAsJsonArray("pages")?.forEach { p ->
            val page = p.asJsonObject
            val title = page.get("title")?.asString ?: return@forEach
            val thumb = page.getAsJsonObject("thumbnail")?.get("source")?.asString ?: return@forEach
            // Only music people: a same-named page about someone else would show the wrong face.
            val description = page.get("description")?.asString.orEmpty()
            if (MEMBER_WORDS.none { description.contains(it, ignoreCase = true) }) return@forEach
            var name = redirectedFrom[title] ?: title
            name = normalizedFrom[name] ?: name
            result[name] = thumb
        }
        return result
    }

    /**
     * For the on-device assistant: Wikipedia search results (title, plain snippet, page URL) for
     * [query]. Keyless; through [NetworkAccessPolicy]. Empty when offline or nothing matched.
     */
    suspend fun webSearch(query: String, limit: Int = 4): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        if (networkPolicy.getDecision(NetworkPurpose.Enrichment) != NetworkDecision.Allowed) return@withContext emptyList()
        val q = android.net.Uri.encode(query)
        val hits = runCatching {
            getJson("https://en.wikipedia.org/w/api.php?action=query&list=search&srlimit=$limit&format=json&srsearch=$q")
                ?.getAsJsonObject("query")?.getAsJsonArray("search")
        }.getOrNull() ?: return@withContext emptyList()
        hits.mapNotNull { h ->
            val o = h.asJsonObject
            val title = o.get("title")?.asString ?: return@mapNotNull null
            val snippet = o.get("snippet")?.asString.orEmpty().replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").replace("&amp;", "&")
            Triple(title, snippet, "https://en.wikipedia.org/wiki/" + android.net.Uri.encode(title.replace(' ', '_')))
        }
    }

    /**
     * For the on-device assistant: a web page as clean text (Jina Reader, keyless), cut to
     * [maxChars] so it fits a phone model's memory. Null when offline or it can't be read.
     */
    suspend fun readPage(url: String, maxChars: Int = 8_000): String? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        if (networkPolicy.getDecision(NetworkPurpose.Enrichment) != NetworkDecision.Allowed) return@withContext null
        runCatching {
            val request = okhttp3.Request.Builder().url("https://r.jina.ai/$url").header("User-Agent", USER_AGENT).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()?.take(maxChars)
            }
        }.getOrNull()
    }

    /** Band members with pictures for [bandName] (cached hub lookup); empty for solo artists. */
    suspend fun rosterFor(bandName: String): List<BandMember> = hubFor(bandName).members

    companion object {
        private const val MAX_MEMBERS = 12
        private const val MIN_BIO_CHARS = 40
        private const val USER_AGENT = "PixelPlayer/1.0 (Android music player)"
        private val MUSIC_WORDS = listOf("band", "singer", "rapper", "musician", "music", "producer", "songwriter", "DJ", "group", "duo", "album")
        private const val MEMBER_PICTURE_CONCURRENCY = 3
        /** A Wikipedia short description must say one of these for its picture to be used for a member. */
        private val MEMBER_WORDS = listOf(
            "musician", "singer", "guitarist", "bassist", "drummer", "vocalist", "keyboardist", "rapper",
            "songwriter", "composer", "producer", "DJ", "percussionist", "violinist", "pianist", "multi-instrumentalist"
        )

        /** Web links → the hub. Official homepage is the website; unrecognised links are dropped. */
        fun socialHubOf(details: MbArtistDetails): ArtistSocialHub {
            val urls = details.relations.mapNotNull { rel -> rel.url?.resource?.takeIf { it.isNotBlank() }?.let { rel.type to it } }
            val website = urls.firstOrNull { (type, _) -> type.equals("official homepage", ignoreCase = true) }?.second
            val platforms = urls
                .filterNot { (type, _) -> type.equals("official homepage", ignoreCase = true) }
                .mapNotNull { (_, url) -> resolvePlatformFromUrl(url) }
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.category.ordinal }, { it.displayName }))
            return ArtistSocialHub(officialWebsite = website, platforms = platforms)
        }

        /** "member of band" relations pointing back at this artist, current members first. */
        fun membersOf(details: MbArtistDetails): List<BandMember> =
            details.relations
                .filter { it.type.equals("member of band", ignoreCase = true) && it.direction == "backward" && it.artist != null }
                .groupBy { it.artist!!.id }
                .map { (_, rels) ->
                    val roles = rels.flatMap { it.attributes }
                        .filterNot { it.equals("original", true) || it.equals("founder", true) }
                        .distinct()
                    BandMember(
                        name = rels.first().artist!!.name,
                        role = roles.joinToString(", ") { it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } },
                        isCurrent = rels.any { !it.ended },
                    )
                }
                .sortedByDescending { it.isCurrent }
    }
}
