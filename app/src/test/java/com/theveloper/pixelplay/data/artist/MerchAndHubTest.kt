package com.theveloper.pixelplay.data.artist

import com.theveloper.pixelplay.data.network.musicbrainz.MbArtist
import com.theveloper.pixelplay.data.network.musicbrainz.MbArtistDetails
import com.theveloper.pixelplay.data.network.musicbrainz.MbArtistRelation
import com.theveloper.pixelplay.data.network.musicbrainz.MbUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchAndHubTest {

    @Test
    fun `merch prices become whole AUD with the given rates`() {
        val rates = mapOf("USD" to 1.5, "GBP" to 2.0, "AUD" to 1.0)
        assertEquals("\$45 AUD", MerchPriceConverter.formatAudPrice("$30.00", rates))
        assertEquals("\$50 AUD", MerchPriceConverter.formatAudPrice("£25.00", rates))
        assertEquals("\$45 AUD", MerchPriceConverter.formatAudPrice("A$45", rates))
        assertNull(MerchPriceConverter.formatAudPrice("  ", rates))
        assertEquals("Free", MerchPriceConverter.formatAudPrice("Free", rates))
    }

    @Test
    fun `currencies are told apart`() {
        assertEquals("CAD", MerchPriceConverter.detectCurrency("CA$20"))
        assertEquals("NZD", MerchPriceConverter.detectCurrency("NZ$20"))
        assertEquals("EUR", MerchPriceConverter.detectCurrency("€20"))
        assertEquals("USD", MerchPriceConverter.detectCurrency("$20"))
    }

    @Test
    fun `links are sorted into the hub and the homepage becomes the website`() {
        val details = MbArtistDetails(
            relations = listOf(
                MbArtistRelation(type = "official homepage", url = MbUrl("https://www.daftpunk.com")),
                MbArtistRelation(type = "social network", url = MbUrl("https://www.instagram.com/daftpunk")),
                MbArtistRelation(type = "streaming", url = MbUrl("https://open.spotify.com/artist/4tZwfgrHOc3mvqYlEYSvVi")),
                MbArtistRelation(type = "social network", url = MbUrl("https://www.dropbox.com/somewhere")),
                MbArtistRelation(type = "social network", url = MbUrl("https://x.com/daftpunk")),
            )
        )
        val hub = ArtistHubRepository.socialHubOf(details)
        assertEquals("https://www.daftpunk.com", hub.officialWebsite)
        assertEquals(setOf("spotify", "instagram", "x_twitter"), hub.platforms.map { it.id }.toSet())
        assertTrue(hub.platforms.first().category == SocialCategory.STREAMING)
    }

    @Test
    fun `band members come back with roles, current ones first`() {
        val details = MbArtistDetails(
            relations = listOf(
                MbArtistRelation(type = "member of band", direction = "backward", artist = MbArtist("1", "Old Drummer"), attributes = listOf("drums"), ended = true),
                MbArtistRelation(type = "member of band", direction = "backward", artist = MbArtist("2", "Singer"), attributes = listOf("original", "lead vocals")),
                MbArtistRelation(type = "member of band", direction = "forward", artist = MbArtist("3", "Other Band")),
            )
        )
        val members = ArtistHubRepository.membersOf(details)
        assertEquals(listOf("Singer", "Old Drummer"), members.map { it.name })
        assertEquals("Lead vocals", members.first().role)
        assertFalse(members.last().isCurrent)
    }
}
