package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.library.RecordingKeys
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.ArtistCatalog
import com.theveloper.pixelplay.data.repository.LyricsLookupMetadata

/**
 * Folds the many copies of one song that a search returns (live bootlegs, remasters, acoustic
 * takes, karaoke, covers…) into one row with a "+N versions" list, like the artist page does
 * with [ArtistCatalog.groupVersions].
 *
 * Rules:
 * - A *family* is every song with the same [ArtistCatalog.baseKey] (title with version words
 *   removed, after the video decorations YouTube titles carry) and the same main artist
 *   ([RecordingKeys.primaryArtist]).
 * - Families by other artists with the same base title join as covers only when the query names
 *   the official artist ("the cure lovesong" folds Adele's "Lovesong" under The Cure), or when
 *   the other song is marked as a cover / karaoke / tribute. "hello" alone keeps Adele's and
 *   Lionel Richie's "Hello" apart: they are different songs.
 * - The row shown is the original (no version tag) by the official artist; ties go to the
 *   earlier search position (the search's own relevance order, our stand-in for popularity),
 *   then the earlier release year.
 * - Versions are the official artist's other cuts first (in search order), then covers.
 *
 * Pure and allocation-light: every regex used here is compiled once (see CLAUDE.md §5).
 */
object SearchVersionGrouping {

    /** Titles or artists that say "this is someone else performing it". */
    private val COVER_MARKER = Regex(
        """(?i)\b(cover|covered by|karaoke|tribute|made famous by|in the style of|originally performed|sing king)\b"""
    )
    private val WORD_SPLIT = Regex("""\s+[-–—]\s+""")
    /** "(Adele cover)", "[Karaoke]", "(Made Famous by The Cure)": removed before keying. */
    private val BRACKETED_COVER = Regex(
        """(?i)\s*[(\[][^)\]]*\b(cover|karaoke|tribute|made famous by|in the style of|originally performed)\b[^)\]]*[)\]]"""
    )
    /** "Lovesong - The Cure cover": the dash suffix that says it's a cover. */
    private val DASH_COVER = Regex(
        """(?i)\s+[-–—]\s+[^-–—]*\b(cover|karaoke|tribute|made famous by|in the style of|originally performed)\b.*$"""
    )

    private class Entry(
        val index: Int,
        val item: SearchResultItem.SongItem,
        val baseKey: String,
        val artistKey: String,
        val isCoverMarked: Boolean,
        val hasVersionTag: Boolean
    )

    /**
     * Returns [items] with songs folded into their families. Non-song items keep their places;
     * each family sits where its first member was.
     */
    fun group(items: List<SearchResultItem>, query: String): List<SearchResultItem> {
        val songs = ArrayList<Entry>()
        items.forEachIndexed { index, item ->
            if (item !is SearchResultItem.SongItem) return@forEachIndexed
            // Already grouped input (shouldn't happen, but be safe): flatten it first.
            val flat = if (item.versions.isEmpty()) listOf(item)
            else listOf(item.copy(versions = emptyList())) + item.versions.map { SearchResultItem.SongItem(it) }
            flat.forEach { songs += entryFor(index, it) }
        }
        if (songs.size < 2) return items

        val foldedQuery = " " + ArtistCatalog.fold(query) + " "
        fun namedInQuery(artistKey: String): Boolean {
            val folded = ArtistCatalog.fold(artistKey)
            return folded.isNotBlank() && foldedQuery.contains(" $folded ")
        }

        // 1. Same base title + same main artist.
        val families = LinkedHashMap<String, MutableList<Entry>>()
        for (entry in songs) families.getOrPut(entry.baseKey + "|" + entry.artistKey) { ArrayList() } += entry

        // 2. Families sharing a base title: pick the official one, fold covers into it.
        val byBase = LinkedHashMap<String, MutableList<MutableList<Entry>>>()
        for (family in families.values) byBase.getOrPut(family.first().baseKey) { ArrayList() } += family

        val groupOf = HashMap<String, Group>() // song id -> its group
        for (sameTitle in byBase.values) {
            val official = sameTitle.firstOrNull { namedInQuery(it.first().artistKey) }
                ?: sameTitle.firstOrNull { family -> family.any { !it.isCoverMarked } }
                ?: sameTitle.first()
            val officialNamed = namedInQuery(official.first().artistKey)
            val covers = ArrayList<Entry>()
            for (family in sameTitle) {
                if (family === official) continue
                val joins = officialNamed || family.all { it.isCoverMarked }
                if (joins) covers += family else register(groupOf, buildGroup(family, emptyList(), namedInQuery(family.first().artistKey)))
            }
            register(groupOf, buildGroup(official, covers, officialNamed))
        }

        // 3. Emit: each group once, at its first member's place.
        val emitted = HashSet<Group>()
        val out = ArrayList<SearchResultItem>(items.size)
        items.forEach { item ->
            if (item !is SearchResultItem.SongItem) {
                out += item
                return@forEach
            }
            val group = groupOf[item.song.id] ?: run { out += item; return@forEach }
            if (emitted.add(group)) out += group.result
        }
        return out
    }

    /** What kind of version a row is, for its badge (the UI maps it to a string resource). */
    enum class VersionKind {
        COVER, REMASTERED, ACOUSTIC, LIVE, REMIX, DEMO, INSTRUMENTAL, KARAOKE, RADIO_EDIT,
        EXTENDED, SPED_UP, SLOWED, RE_RECORDED, OTHER
    }

    /** [rawTag] is the bracketed text from the title, shown as-is for [VersionKind.OTHER]. */
    data class VersionBadge(val kind: VersionKind, val rawTag: String? = null)

    /** Badge for a version row, or null when the title names no version (another release). */
    fun versionBadge(version: Song, canonical: Song): VersionBadge? {
        if (isCover(version, canonical)) return VersionBadge(VersionKind.COVER)
        val title = runCatching { LyricsLookupMetadata.cleanTitle(version.title) }.getOrDefault(version.title)
        val tag = ArtistCatalog.versionTag(title)
            ?: return if (COVER_MARKER.containsMatchIn(version.title)) VersionBadge(VersionKind.COVER) else null
        val lower = tag.lowercase()
        val kind = when {
            "karaoke" in lower -> VersionKind.KARAOKE
            "remaster" in lower -> VersionKind.REMASTERED
            "unplugged" in lower || "acoustic" in lower -> VersionKind.ACOUSTIC
            "live" in lower -> VersionKind.LIVE
            "remix" in lower || lower.endsWith(" mix") || lower == "mix" -> VersionKind.REMIX
            "demo" in lower -> VersionKind.DEMO
            "instrumental" in lower -> VersionKind.INSTRUMENTAL
            "radio edit" in lower || lower == "edit" -> VersionKind.RADIO_EDIT
            "extended" in lower -> VersionKind.EXTENDED
            "sped up" in lower -> VersionKind.SPED_UP
            "slowed" in lower -> VersionKind.SLOWED
            "re-recorded" in lower || "taylor's version" in lower -> VersionKind.RE_RECORDED
            else -> VersionKind.OTHER
        }
        return VersionBadge(kind, tag.trim())
    }

    /** True when [version] is by a different main artist than [canonical] (a cover). */
    fun isCover(version: Song, canonical: Song): Boolean =
        RecordingKeys.primaryArtist(version.artist) != RecordingKeys.primaryArtist(canonical.artist)

    private class Group(val result: SearchResultItem.SongItem, val memberIds: List<String>)

    private fun register(groupOf: HashMap<String, Group>, group: Group) {
        group.memberIds.forEach { groupOf[it] = group }
    }

    private fun buildGroup(official: List<Entry>, covers: List<Entry>, isOfficialArtist: Boolean): Group {
        val canonical = official.minWithOrNull(
            compareBy<Entry> { if (it.hasVersionTag) 1 else 0 }
                .thenBy { if (it.isCoverMarked) 1 else 0 }
                .thenBy { it.index }
                .thenBy { it.item.song.year.takeIf { y -> y > 0 } ?: Int.MAX_VALUE }
        ) ?: official.first()
        val versions = official.filter { it !== canonical }.sortedBy { it.index }.map { it.item.song } +
            covers.sortedBy { it.index }.map { it.item.song }
        val distinctVersions = versions.distinctBy { it.id }.filter { it.id != canonical.item.song.id }
        val result = canonical.item.copy(versions = distinctVersions, isOfficialArtist = isOfficialArtist)
        return Group(result, (official + covers).map { it.item.song.id })
    }

    private fun entryFor(index: Int, item: SearchResultItem.SongItem): Entry {
        val song = item.song
        val artistKey = runCatching { RecordingKeys.primaryArtist(song.artist) }.getOrDefault(song.artist.lowercase())
        val title = cleanTitle(song.title, artistKey)
        val marked = COVER_MARKER.containsMatchIn(song.title) || COVER_MARKER.containsMatchIn(song.artist)
        return Entry(
            index = index,
            item = item,
            baseKey = ArtistCatalog.baseKey(title),
            artistKey = artistKey,
            isCoverMarked = marked,
            hasVersionTag = ArtistCatalog.versionTag(title) != null
        )
    }

    /** "The Cure - Lovesong (Official Video)" -> "Lovesong". */
    private fun cleanTitle(raw: String, artistKey: String): String {
        var title = runCatching { LyricsLookupMetadata.cleanTitle(raw) }.getOrDefault(raw)
        val parts = title.split(WORD_SPLIT, limit = 2)
        if (parts.size == 2 && artistKey.isNotBlank()) {
            val left = ArtistCatalog.fold(parts[0])
            val artist = ArtistCatalog.fold(artistKey)
            if (left.isNotBlank() && (left == artist || left.contains(artist) || artist.contains(left))) title = parts[1]
        }
        title = DASH_COVER.replace(BRACKETED_COVER.replace(title, ""), "").trim()
        return title.ifBlank { raw }
    }
}
