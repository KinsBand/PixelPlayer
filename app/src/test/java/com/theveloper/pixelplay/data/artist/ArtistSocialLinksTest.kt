package com.theveloper.pixelplay.data.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistSocialLinksTest {

    @Test
    fun testStreamingPlatformResolutions() {
        val spotify = resolvePlatformFromUrl("https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb")
        assertNotNull(spotify)
        assertEquals(ArtistPlatformIds.SPOTIFY, spotify?.id)
        assertEquals(SocialCategory.STREAMING, spotify?.category)
        assertEquals("spotify:artist:4Z8W4fKeB5YxbusRsdQVPb", spotify?.appUri)

        val ytMusic = resolvePlatformFromUrl("https://music.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ")
        assertNotNull(ytMusic)
        assertEquals(ArtistPlatformIds.YOUTUBE_MUSIC, ytMusic?.id)
        assertEquals(SocialCategory.STREAMING, ytMusic?.category)

        val appleMusic = resolvePlatformFromUrl("https://music.apple.com/us/artist/radiohead/657515")
        assertNotNull(appleMusic)
        assertEquals(ArtistPlatformIds.APPLE_MUSIC, appleMusic?.id)
        assertEquals(SocialCategory.STREAMING, appleMusic?.category)

        val soundCloud = resolvePlatformFromUrl("https://soundcloud.com/radiohead")
        assertNotNull(soundCloud)
        assertEquals(ArtistPlatformIds.SOUNDCLOUD, soundCloud?.id)
        assertEquals(SocialCategory.STREAMING, soundCloud?.category)

        val deezer = resolvePlatformFromUrl("https://www.deezer.com/artist/395")
        assertNotNull(deezer)
        assertEquals(ArtistPlatformIds.DEEZER, deezer?.id)
        assertEquals(SocialCategory.STREAMING, deezer?.category)

        val tidal = resolvePlatformFromUrl("https://tidal.com/artist/194")
        assertNotNull(tidal)
        assertEquals(ArtistPlatformIds.TIDAL, tidal?.id)
        assertEquals(SocialCategory.STREAMING, tidal?.category)

        val bandcamp = resolvePlatformFromUrl("https://radiohead.bandcamp.com")
        assertNotNull(bandcamp)
        assertEquals(ArtistPlatformIds.BANDCAMP, bandcamp?.id)
        assertEquals(SocialCategory.STREAMING, bandcamp?.category)

        val amazonMusic = resolvePlatformFromUrl("https://music.amazon.com/artists/B000QJOC2C")
        assertNotNull(amazonMusic)
        assertEquals(ArtistPlatformIds.AMAZON_MUSIC, amazonMusic?.id)
        assertEquals(SocialCategory.STREAMING, amazonMusic?.category)
    }

    @Test
    fun testSocialPlatformResolutions() {
        val insta = resolvePlatformFromUrl("https://instagram.com/radiohead")
        assertNotNull(insta)
        assertEquals(ArtistPlatformIds.INSTAGRAM, insta?.id)
        assertEquals(SocialCategory.SOCIAL, insta?.category)

        val tiktok = resolvePlatformFromUrl("https://tiktok.com/@radiohead")
        assertNotNull(tiktok)
        assertEquals(ArtistPlatformIds.TIKTOK, tiktok?.id)
        assertEquals(SocialCategory.SOCIAL, tiktok?.category)

        val yt = resolvePlatformFromUrl("https://www.youtube.com/@radiohead")
        assertNotNull(yt)
        assertEquals(ArtistPlatformIds.YOUTUBE, yt?.id)
        assertEquals(SocialCategory.SOCIAL, yt?.category)

        val twitter = resolvePlatformFromUrl("https://x.com/radiohead")
        assertNotNull(twitter)
        assertEquals(ArtistPlatformIds.X_TWITTER, twitter?.id)
        assertEquals(SocialCategory.SOCIAL, twitter?.category)

        val fb = resolvePlatformFromUrl("https://facebook.com/radiohead")
        assertNotNull(fb)
        assertEquals(ArtistPlatformIds.FACEBOOK, fb?.id)
        assertEquals(SocialCategory.SOCIAL, fb?.category)

        val threads = resolvePlatformFromUrl("https://www.threads.net/@radiohead")
        assertNotNull(threads)
        assertEquals(ArtistPlatformIds.THREADS, threads?.id)
        assertEquals(SocialCategory.SOCIAL, threads?.category)

        val snap = resolvePlatformFromUrl("https://snapchat.com/add/radiohead")
        assertNotNull(snap)
        assertEquals(ArtistPlatformIds.SNAPCHAT, snap?.id)
        assertEquals(SocialCategory.SOCIAL, snap?.category)

        val linkedin = resolvePlatformFromUrl("https://linkedin.com/company/radiohead")
        assertNotNull(linkedin)
        assertEquals(ArtistPlatformIds.LINKEDIN, linkedin?.id)
        assertEquals(SocialCategory.SOCIAL, linkedin?.category)

        val bsky = resolvePlatformFromUrl("https://bsky.app/profile/radiohead.bsky.social")
        assertNotNull(bsky)
        assertEquals(ArtistPlatformIds.BLUESKY, bsky?.id)
        assertEquals(SocialCategory.SOCIAL, bsky?.category)
    }

    @Test
    fun testCommunityPlatformResolutions() {
        val discord = resolvePlatformFromUrl("https://discord.gg/radiohead")
        assertNotNull(discord)
        assertEquals(ArtistPlatformIds.DISCORD, discord?.id)
        assertEquals(SocialCategory.COMMUNITY, discord?.category)

        val reddit = resolvePlatformFromUrl("https://reddit.com/r/radiohead")
        assertNotNull(reddit)
        assertEquals(ArtistPlatformIds.REDDIT, reddit?.id)
        assertEquals(SocialCategory.COMMUNITY, reddit?.category)

        val substack = resolvePlatformFromUrl("https://radiohead.substack.com")
        assertNotNull(substack)
        assertEquals(ArtistPlatformIds.SUBSTACK, substack?.id)
        assertEquals(SocialCategory.COMMUNITY, substack?.category)

        val patreon = resolvePlatformFromUrl("https://patreon.com/radiohead")
        assertNotNull(patreon)
        assertEquals(ArtistPlatformIds.PATREON, patreon?.id)
        assertEquals(SocialCategory.COMMUNITY, patreon?.category)

        val twitch = resolvePlatformFromUrl("https://twitch.tv/radiohead")
        assertNotNull(twitch)
        assertEquals(ArtistPlatformIds.TWITCH, twitch?.id)
        assertEquals(SocialCategory.COMMUNITY, twitch?.category)

        val pinterest = resolvePlatformFromUrl("https://pinterest.com/radiohead")
        assertNotNull(pinterest)
        assertEquals(ArtistPlatformIds.PINTEREST, pinterest?.id)
        assertEquals(SocialCategory.COMMUNITY, pinterest?.category)
    }

    @Test
    fun testUnrecognizedOrBlankUrl() {
        assertNull(resolvePlatformFromUrl(""))
        assertNull(resolvePlatformFromUrl("https://example.com"))
    }
}
