package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.LyricsLookupMetadata
import java.util.Locale
import kotlin.math.abs

/**
 * One row per recording.
 *
 * The same recording can reach a list several times: a local file, a download (library row
 * with a hashed id), its cloud row (`yt_…`), a Spotify/YouTube Music like (`spotify_…`), a
 * favourites-playlist copy. [unify] merges those into one [UnifiedSong] whose state is the union
 * (liked if any copy is liked, downloaded if any copy is downloaded).
 *
 * Versions (live, remastered, acoustic, covers…) are *different* recordings and are never merged;
 * [families] groups them under their original instead.
 */
object SongUnifier {

    data class UnifiedSong(
        /** The copy to play and show, with merged state. */
        val song: Song,
        /** Every copy of this recording (including [song]'s original), for unliking all of them. */
        val copies: List<Song>,
        val isLiked: Boolean,
    ) {
        val id: String get() = song.id
    }

    /** A song and its other versions (live, remaster, cover…). [versions] excludes [head]. */
    data class Family(val head: UnifiedSong, val versions: List<UnifiedSong>) {
        val key: String get() = head.id
    }

    private const val SAME_DURATION_MS = 3_000L

    /**
     * Merges copies of the same recording. Order follows the first appearance of each recording
     * in [songs]. [likedIds] are the ids of liked copies (any source).
     */
    fun unify(songs: List<Song>, likedIds: Set<String>): List<UnifiedSong> {
        if (songs.isEmpty()) return emptyList()
        val parent = IntArray(songs.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) { if (ra < rb) parent[rb] = ra else parent[ra] = rb }
        }

        // 1. Shared identities: ids, YouTube ids, downloaded-library ids, ISRC.
        val owner = HashMap<String, Int>(songs.size * 3)
        songs.forEachIndexed { index, song ->
            for (key in identities(song)) {
                val previous = owner.putIfAbsent(key, index)
                if (previous != null) union(previous, index)
            }
        }
        // 2. Same recording key (title + main artist, version words kept) and similar length.
        val byRecording = HashMap<String, MutableList<Int>>()
        songs.forEachIndexed { index, song ->
            val key = runCatching { RecordingKeys.of(song) }.getOrNull() ?: return@forEachIndexed
            if (key.length <= 1 || key.startsWith("|") || key.endsWith("|")) return@forEachIndexed
            byRecording.getOrPut(key) { mutableListOf() }.add(index)
        }
        for (group in byRecording.values) {
            if (group.size < 2) continue
            for (i in group.indices) for (j in i + 1 until group.size) {
                val a = songs[group[i]].duration
                val b = songs[group[j]].duration
                // The key ignores edition words ("Remastered", "Live Version"), so a different
                // version label means a different recording even at the same length.
                if (versionLabel(songs[group[i]]) != versionLabel(songs[group[j]])) continue
                if (a <= 0 || b <= 0 || abs(a - b) <= SAME_DURATION_MS) union(group[i], group[j])
            }
        }

        val groups = LinkedHashMap<Int, MutableList<Int>>()
        songs.indices.forEach { groups.getOrPut(find(it)) { mutableListOf() }.add(it) }
        return groups.values.map { members -> merge(members.map { songs[it] }, likedIds) }
    }

    private fun identities(song: Song): Set<String> = buildSet {
        add("id:" + song.id)
        song.youtubeId?.takeIf { it.isNotBlank() }?.let { add("yt:$it") }
        if (song.id.startsWith("yt_")) add("yt:" + song.id.removePrefix("yt_"))
        // A download's library row uses a hash of its cloud id.
        if (!song.id.all { it.isDigit() || it == '-' }) {
            add("id:" + DownloadedLibraryIndexer.libraryId(song.id))
        }
        song.creditsAndRelease.isrc?.trim()?.takeIf { it.isNotBlank() }?.let { add("isrc:" + it.uppercase(Locale.ROOT)) }
    }

    private fun rank(song: Song): Int = when {
        song.id.toLongOrNull() != null && song.isLocal && !song.isDownloaded -> 0 // local file
        song.isDownloaded || song.id.toLongOrNull() != null -> 1                 // download
        song.youtubeId != null -> 2                                              // streamable
        else -> 3
    }

    private fun merge(copies: List<Song>, likedIds: Set<String>): UnifiedSong {
        val best = copies.minBy(::rank)
        val liked = copies.any { it.isFavorite || it.id in likedIds || (it.youtubeId != null && "yt_${it.youtubeId}" in likedIds) }
        val downloaded = copies.any { it.isDownloaded }
        val merged = best.copy(
            isFavorite = liked,
            downloadState = if (downloaded) DownloadState.DOWNLOADED else best.downloadState,
            youtubeId = best.youtubeId ?: copies.firstNotNullOfOrNull { it.youtubeId },
            albumArtUriString = best.albumArtUriString?.takeIf { it.isNotBlank() }
                ?: copies.firstNotNullOfOrNull { it.albumArtUriString?.takeIf { art -> art.isNotBlank() } },
            genre = best.genre ?: copies.firstNotNullOfOrNull { it.genre },
            duration = best.duration.takeIf { it > 0 } ?: copies.maxOf { it.duration },
        )
        return UnifiedSong(merged, copies, liked)
    }

    // ---- Versions ------------------------------------------------------------------------------

    private val nonAlnum = Regex("""[^\p{L}\p{N}]+""")
    private val bracketed = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val dashSuffix = Regex("""\s+[-–—]\s+.*$""")

    private val labels = listOf(
        "Live" to Regex("""\blive\b|\bin concert\b|\bunplugged\b""", RegexOption.IGNORE_CASE),
        "Cover" to Regex("""\bcover(ed)?\b|\boriginally (performed )?by\b""", RegexOption.IGNORE_CASE),
        "Remastered" to Regex("""\bremaster(ed)?\b""", RegexOption.IGNORE_CASE),
        "Acoustic" to Regex("""\bacoustic\b""", RegexOption.IGNORE_CASE),
        "Remix" to Regex("""\bremix\b|\brmx\b|\bmix\)|\bbootleg\b""", RegexOption.IGNORE_CASE),
        "Instrumental" to Regex("""\binstrumental\b|\bkaraoke\b""", RegexOption.IGNORE_CASE),
        "Demo" to Regex("""\bdemo\b""", RegexOption.IGNORE_CASE),
        "Sped up" to Regex("""\bsped up\b|\bnightcore\b|\bslowed\b|\breverb\b""", RegexOption.IGNORE_CASE),
        "Extended" to Regex("""\bextended\b""", RegexOption.IGNORE_CASE),
        "Edit" to Regex("""\bradio edit\b|\bedit\b""", RegexOption.IGNORE_CASE),
        "Version" to Regex("""\bversion\b|\bre-?recorded\b|\btaylor'?s version\b""", RegexOption.IGNORE_CASE),
    )

    /** "Live", "Remastered", "Cover"…; null for the original. Read from the title's decorations. */
    fun versionLabel(song: Song): String? {
        val decorations = (bracketed.findAll(song.title).joinToString(" ") { it.value } + " " +
            (dashSuffix.find(song.title)?.value ?: "")).trim()
        if (decorations.isBlank()) return null
        return labels.firstOrNull { (_, pattern) -> pattern.containsMatchIn(decorations) }?.first
    }

    /** Title with every decoration removed: "Song (Live at X) - 2011 Remaster" -> "song". */
    fun baseTitle(title: String): String {
        var t = runCatching { LyricsLookupMetadata.cleanTitle(title) }.getOrDefault(title)
        t = bracketed.replace(t, "")
        t = dashSuffix.replace(t, "")
        t = Regex("""\b(feat|ft)\.?\s.*$""", RegexOption.IGNORE_CASE).replace(t, "")
        return nonAlnum.replace(t.lowercase(Locale.ROOT), " ").trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Groups songs into families of versions, in the order the heads first appear. A family is the
     * same base title by the same main artist; a song labelled "Cover" joins a family with the same
     * base title by any artist.
     */
    fun families(songs: List<UnifiedSong>): List<Family> {
        if (songs.isEmpty()) return emptyList()
        val byKey = LinkedHashMap<String, MutableList<UnifiedSong>>()
        val byTitle = HashMap<String, String>() // base title -> first family key
        val covers = ArrayList<Pair<String, UnifiedSong>>()
        for (entry in songs) {
            val base = baseTitle(entry.song.title)
            if (base.isBlank()) { byKey.getOrPut("id:" + entry.id) { mutableListOf() }.add(entry); continue }
            if (versionLabel(entry.song) == "Cover") { covers += base to entry; continue }
            val key = base + "|" + RecordingKeys.primaryArtist(entry.song.artist)
            byKey.getOrPut(key) { mutableListOf() }.add(entry)
            byTitle.putIfAbsent(base, key)
        }
        for ((base, entry) in covers) {
            val key = byTitle[base] ?: (base + "|" + RecordingKeys.primaryArtist(entry.song.artist))
            byKey.getOrPut(key) { mutableListOf() }.add(entry)
        }
        return byKey.values.map { members ->
            val head = members.firstOrNull { versionLabel(it.song) == null }
                ?: members.firstOrNull { it.isLiked }
                ?: members.first()
            Family(head, members.filter { it !== head })
        }
    }
}
