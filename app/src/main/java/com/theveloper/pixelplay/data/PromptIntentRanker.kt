package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import java.util.Locale

/**
 * Ranks songs for a [PromptIntent] with the same scorer the vibe filters and the mix use
 * (MusicVibeFilters.score), plus credit for named artists / songs. Pure: no I/O, runs on any
 * thread. Used where a typed request becomes a list of songs (AI playlists).
 */
object PromptIntentRanker {

    fun rank(
        intent: PromptIntent,
        songs: List<Song>,
        limit: Int,
        likedIds: Set<String> = emptySet(),
        maxPerArtist: Int = 4,
    ): List<Song> {
        if (songs.isEmpty() || limit <= 0) return emptyList()
        val artistNames = intent.artists.mapTo(HashSet()) { it.lowercase(Locale.ROOT).trim() }
        val titleNames = intent.songs.map { it.lowercase(Locale.ROOT).trim() }
        val namedSeeds = songs.filter { s ->
            val title = s.title.lowercase(Locale.ROOT)
            titleNames.any { it.isNotEmpty() && title.contains(it) }
        }
        val affinity = MixAffinityIndex(
            (namedSeeds + songs.filter { it.artist.trim().lowercase(Locale.ROOT) in artistNames }.take(6))
                .distinctBy(::mixIdentity)
        )
        val filter = intent.toFilter()
        val excluded = intent.excludeGenres
        fun isExcluded(song: Song): Boolean {
            if (excluded.isEmpty()) return false
            val tags = (song.genre.orEmpty() + " " + song.songInformation.subgenre.orEmpty()).lowercase(Locale.ROOT)
            return excluded.any { tag -> Regex("(?<![a-z0-9])" + Regex.escape(tag) + "(?![a-z0-9])").containsMatchIn(tags) }
        }
        val scored = songs.asSequence()
            .filterNot(::isExcluded)
            .map { song ->
                var score = if (intent.hasVibe || intent.freeWords.isNotEmpty()) MusicVibeFilters.score(song, filter) else 0.0
                val artist = song.artist.trim().lowercase(Locale.ROOT)
                if (artist in artistNames || artistNames.any { it.length >= 3 && artist.contains(it) }) score += 4.0
                score += affinity.affinity(song) * 0.35
                if (song.id in likedIds) score += 0.5
                song to score
            }
            .filter { it.second >= MusicVibeFilters.MATCH_THRESHOLD }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
        val perArtist = HashMap<String, Int>()
        return (namedSeeds + scored).distinctBy(::mixIdentity).filter { song ->
            val key = song.artist.trim().lowercase(Locale.ROOT)
            val n = perArtist.getOrDefault(key, 0)
            if (n >= maxPerArtist) false else { perArtist[key] = n + 1; true }
        }.take(limit)
    }
}
