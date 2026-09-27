package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song

/**
 * Recent choices steer the shelf; every fourth slot offers a related, unheard track.
 *
 * Each song's identity and affinity are computed once. The previous version called
 * [mixAffinity] inside the sort comparator, so a library ranked against its liked songs
 * did O(n log n x seeds) work with fresh strings on every call — this ran on the main
 * thread from the home screen and caused long freezes and GC storms.
 */
internal fun personalizedQuickPicks(
    candidates: List<Song>, recent: List<Song>, favorites: Set<String>, limit: Int = 20
): List<Song> {
    if (limit <= 0) return emptyList()
    val pool = candidates.distinctBy(::mixIdentity)
    val identities = HashMap<String, String>(pool.size * 2)
    pool.forEach { identities[it.id] = mixIdentity(it) }
    fun idOf(song: Song) = identities[song.id] ?: mixIdentity(song)

    val recentKeys = recent.map(::mixIdentity).toSet()
    val seeds = (recent.take(12) + pool.filter { it.id in favorites }).distinctBy(::mixIdentity)
    val index = MixAffinityIndex(seeds)
    val affinity = HashMap<String, Int>(pool.size * 2)
    pool.forEach { affinity[it.id] = index.affinity(it) }

    val score = HashMap<String, Int>(pool.size * 2)
    pool.forEach { song ->
        score[song.id] = (affinity[song.id] ?: 0) + (if (song.id in favorites) 8 else 0) +
            (if (idOf(song) in recentKeys) 3 else 0)
    }
    val ranked = pool.sortedByDescending { score[it.id] ?: 0 }
    val discoveries = ranked.filter {
        idOf(it) !in recentKeys && it.id !in favorites && (affinity[it.id] ?: 0) > 0
    }.toMutableList()
    val familiar = ranked.filter { idOf(it) in recentKeys || it.id in favorites }.toMutableList()
    val remaining = ranked.toMutableList()
    val result = mutableListOf<Song>()
    while (result.size < limit && remaining.isNotEmpty()) {
        val preferred = if (result.size % 4 == 3) discoveries else familiar
        val choices = preferred.ifEmpty { remaining }
        val next = choices.firstOrNull { it.artist != result.lastOrNull()?.artist } ?: choices.first()
        result += next
        remaining.remove(next)
        discoveries.remove(next)
        familiar.remove(next)
    }
    return result
}
