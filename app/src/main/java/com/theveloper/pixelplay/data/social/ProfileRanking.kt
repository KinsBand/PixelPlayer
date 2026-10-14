package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.repository.ArtistCatalog

/**
 * What PixelPlayer knows about people when ranking a people search: names you see them by
 * (your alias and their platform name, per Spotify user id), who you follow each other with,
 * and who you follow.
 */
data class ProfileRankContext(
    /** Spotify user id → names (alias first) of people who are your PixelPlayer friends. */
    val friendNames: Map<String, List<String>> = emptyMap(),
    /** Spotify user ids you follow each other with. */
    val mutual: Set<String> = emptySet(),
    /** Spotify user ids you follow. */
    val following: Set<String> = emptySet(),
)

/**
 * Pure people-search ranking (Add friend and Search → Profiles). Name match first (exact >
 * starts with > a word starts with > contains > user id), accents and case ignored, then a
 * boost for your friends, mutuals and people you follow. Ties keep the incoming order (Spotify's).
 */
object ProfileRanking {
    private const val EXACT = 100
    private const val PREFIX = 70
    private const val WORD_PREFIX = 50
    private const val CONTAINS = 30
    private const val ID_MATCH = 20
    private const val FRIEND_BOOST = 25
    private const val MUTUAL_BOOST = 15
    private const val FOLLOWING_BOOST = 10

    private fun boost(person: SocialPerson, ctx: ProfileRankContext): Int =
        (if (person.userId in ctx.friendNames) FRIEND_BOOST else 0) +
            (if (person.userId in ctx.mutual) MUTUAL_BOOST else 0) +
            (if (person.userId in ctx.following) FOLLOWING_BOOST else 0)

    private fun nameScore(folded: String, q: String): Int = when {
        folded.isEmpty() -> 0
        folded == q -> EXACT
        folded.startsWith(q) -> PREFIX
        folded.split(' ').any { it.startsWith(q) } -> WORD_PREFIX
        folded.contains(q) -> CONTAINS
        else -> 0
    }

    /** 0 = doesn't match a non-blank [query]. A blank query scores everyone by the boost only. */
    fun score(query: String, person: SocialPerson, ctx: ProfileRankContext): Int {
        val q = ArtistCatalog.fold(query)
        if (q.isEmpty()) return boost(person, ctx)
        val names = listOf(person.name) + ctx.friendNames[person.userId].orEmpty()
        var best = names.maxOf { nameScore(ArtistCatalog.fold(it), q) }
        if (best == 0 && ArtistCatalog.fold(person.userId).replace(" ", "").contains(q.replace(" ", ""))) best = ID_MATCH
        return if (best == 0) 0 else best + boost(person, ctx)
    }

    /** Best first; stable. With a non-blank query, people that don't match are dropped. */
    fun rank(query: String, people: List<SocialPerson>, ctx: ProfileRankContext): List<SocialPerson> {
        val blank = query.isBlank()
        return people.map { it to score(query, it, ctx) }
            .filter { (_, s) -> blank || s > 0 }
            .sortedByDescending { (_, s) -> s }
            .map { it.first }
    }
}
