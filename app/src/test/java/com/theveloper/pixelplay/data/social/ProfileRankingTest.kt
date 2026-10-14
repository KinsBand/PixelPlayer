package com.theveloper.pixelplay.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Guards people search ranking: name match strength, accents, aliases, friend boosts, stability. */
class ProfileRankingTest {
    private fun p(id: String, name: String) = SocialPerson(userId = id, name = name, avatarUrl = null, followers = -1)

    @Test fun `exact beats prefix beats word prefix beats contains`() {
        val people = listOf(p("1", "Osama"), p("2", "Big Sam"), p("3", "Samantha"), p("4", "sam"))
        assertEquals(listOf("4", "3", "2", "1"), ProfileRanking.rank("sam", people, ProfileRankContext()).map { it.userId })
    }

    @Test fun `accents are ignored`() {
        assertEquals(1, ProfileRanking.rank("jose", listOf(p("1", "José")), ProfileRankContext()).size)
    }

    @Test fun `alias finds a friend and friends rank first`() {
        val ctx = ProfileRankContext(friendNames = mapOf("9" to listOf("Mum", "Jane Smith")))
        val people = listOf(p("1", "Mumford"), p("9", "Jane Smith"))
        assertEquals(listOf("9", "1"), ProfileRanking.rank("mum", people, ctx).map { it.userId })
    }

    @Test fun `non matches are dropped, ties keep order`() {
        val people = listOf(p("a", "Alex"), p("b", "Bob"), p("c", "Alexa"))
        // Alex exact (100), Alexa prefix (70); Bob dropped.
        assertEquals(listOf("a", "c"), ProfileRanking.rank("alex", people, ProfileRankContext()).map { it.userId })
        val tie = listOf(p("x", "Kim A"), p("y", "Kim B"))
        assertEquals(listOf("x", "y"), ProfileRanking.rank("kim", tie, ProfileRankContext()).map { it.userId })
    }

    @Test fun `blank query keeps everyone, boosted first`() {
        val ctx = ProfileRankContext(mutual = setOf("b"))
        assertEquals(listOf("b", "a"), ProfileRanking.rank("", listOf(p("a", "A"), p("b", "B")), ctx).map { it.userId })
    }
}
