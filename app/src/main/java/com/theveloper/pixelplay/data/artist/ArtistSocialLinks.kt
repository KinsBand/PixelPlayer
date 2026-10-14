package com.theveloper.pixelplay.data.artist

import android.app.SearchManager
import android.content.Intent
import java.util.Locale

/**
 * Categorization of external artist destinations and platforms.
 */
enum class SocialCategory {
    STREAMING,
    SOCIAL,
    COMMUNITY
}

/**
 * Information about a resolved or configured external social/streaming platform.
 *
 * @property id Unique identifier for the platform (e.g., "spotify", "instagram", "discord").
 * @property displayName User-friendly platform name.
 * @property category Category of the platform (STREAMING, SOCIAL, or COMMUNITY).
 * @property webUrl Full HTTPS web link to the artist page/profile.
 * @property appUri Optional deep link or URI to launch directly into the native app if installed.
 * @property iconRes Optional drawable resource ID for rendering platform brand icon.
 */
data class SocialPlatformInfo(
    val id: String,
    val displayName: String,
    val category: SocialCategory,
    val webUrl: String,
    val appUri: String? = null,
    val iconRes: Int? = null
)

/**
 * Aggregated social and external links hub for an artist.
 *
 * @property officialWebsite Direct URL to the artist's official homepage or domain, if known.
 * @property platforms Ordered list of verified social, streaming, and community links.
 */
data class ArtistSocialHub(
    val officialWebsite: String? = null,
    val platforms: List<SocialPlatformInfo> = emptyList()
) {
    /** Helper getters filtered by category */
    val streamingPlatforms: List<SocialPlatformInfo>
        get() = platforms.filter { it.category == SocialCategory.STREAMING }

    val socialPlatforms: List<SocialPlatformInfo>
        get() = platforms.filter { it.category == SocialCategory.SOCIAL }

    val communityPlatforms: List<SocialPlatformInfo>
        get() = platforms.filter { it.category == SocialCategory.COMMUNITY }
}

/**
 * Standard platform identifiers.
 */
object ArtistPlatformIds {
    // Streaming
    const val SPOTIFY = "spotify"
    const val YOUTUBE_MUSIC = "youtube_music"
    const val APPLE_MUSIC = "apple_music"
    const val SOUNDCLOUD = "soundcloud"
    const val DEEZER = "deezer"
    const val TIDAL = "tidal"
    const val BANDCAMP = "bandcamp"
    const val AMAZON_MUSIC = "amazon_music"

    // Social Media
    const val INSTAGRAM = "instagram"
    const val TIKTOK = "tiktok"
    const val YOUTUBE = "youtube"
    const val X_TWITTER = "x_twitter"
    const val FACEBOOK = "facebook"
    const val THREADS = "threads"
    const val SNAPCHAT = "snapchat"
    const val LINKEDIN = "linkedin"
    const val BLUESKY = "bluesky"

    // Community
    const val DISCORD = "discord"
    const val REDDIT = "reddit"
    const val SUBSTACK = "substack"
    const val PATREON = "patreon"
    const val TWITCH = "twitch"
    const val PINTEREST = "pinterest"
}

/**
 * Identifies the platform and category from a given URL string.
 *
 * Supports all 3.7 platforms:
 * - Streaming: Spotify, YouTube Music, Apple Music, SoundCloud, Deezer, Tidal, Bandcamp, Amazon Music.
 * - Social Media: Instagram, TikTok, YouTube, X (Twitter), Facebook, Threads, Snapchat, LinkedIn, Bluesky.
 * - Community: Discord, Reddit, Substack, Patreon, Twitch, Pinterest.
 *
 * @param url Target URL or profile link.
 * @return Resolved [SocialPlatformInfo] with appropriate category and metadata, or null if unrecognized.
 */
fun resolvePlatformFromUrl(url: String): SocialPlatformInfo? {
    if (url.isBlank()) return null

    val normalized = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        "https://$url"
    } else {
        url
    }

    // Pure Kotlin URL parsing independent of android.net.Uri stubs
    val urlWithoutScheme = if (normalized.contains("://")) normalized.substringAfter("://") else normalized
    val hostAndPort = urlWithoutScheme.substringBefore("/").substringBefore("?").lowercase(Locale.ROOT)
    val host = hostAndPort.substringBefore(":")
    val path = if (urlWithoutScheme.contains("/")) {
        "/" + urlWithoutScheme.substringAfter("/").substringBefore("?").lowercase(Locale.ROOT)
    } else {
        ""
    }

    if (host.isBlank()) return null

    // Helper to extract native app URIs where supported
    fun spotifyAppUri(originalUrl: String): String? {
        val artistIdMatch = Regex("""/artist/([a-zA-Z0-9]+)""").find(originalUrl)
        return artistIdMatch?.groupValues?.get(1)?.let { "spotify:artist:$it" }
    }

    fun instagramAppUri(originalUrl: String): String? {
        val userMatch = Regex("""instagram\.com/([a-zA-Z0-9_.]+)""").find(originalUrl)
        return userMatch?.groupValues?.get(1)?.let { "instagram://user?username=$it" }
    }

    fun twitterAppUri(originalUrl: String): String? {
        val handleMatch = Regex("""(?:twitter|x)\.com/([a-zA-Z0-9_]+)""").find(originalUrl)
        return handleMatch?.groupValues?.get(1)?.let { "twitter://user?screen_name=$it" }
    }

    return when {
        // STREAMING
        host == "spotify.com" || host.endsWith(".spotify.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.SPOTIFY,
                displayName = "Spotify",
                category = SocialCategory.STREAMING,
                webUrl = normalized,
                appUri = spotifyAppUri(normalized)
            )
        }
        host == "music.youtube.com" || (host.contains("youtube.com") && path.startsWith("/music")) -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.YOUTUBE_MUSIC,
                displayName = "YouTube Music",
                category = SocialCategory.STREAMING,
                webUrl = normalized
            )
        }
        host.contains("music.apple.com") || host.contains("itunes.apple.com") ||
            (host.contains("apple.com") && path.contains("/music")) -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.APPLE_MUSIC,
                displayName = "Apple Music",
                category = SocialCategory.STREAMING,
                webUrl = normalized
            )
        }
        host.contains("soundcloud.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.SOUNDCLOUD,
                displayName = "SoundCloud",
                category = SocialCategory.STREAMING,
                webUrl = normalized
            )
        }
        host.contains("deezer.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.DEEZER,
                displayName = "Deezer",
                category = SocialCategory.STREAMING,
                webUrl = normalized
            )
        }
        host.contains("tidal.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.TIDAL,
                displayName = "Tidal",
                category = SocialCategory.STREAMING,
                webUrl = normalized
            )
        }
        host.contains("bandcamp.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.BANDCAMP,
                displayName = "Bandcamp",
                category = SocialCategory.STREAMING,
                webUrl = normalized
            )
        }
        host.contains("music.amazon.") || (host.contains("amazon.") && (path.contains("/music") || path.contains("/artist"))) -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.AMAZON_MUSIC,
                displayName = "Amazon Music",
                category = SocialCategory.STREAMING,
                webUrl = normalized
            )
        }

        // SOCIAL MEDIA
        host.contains("instagram.com") || host.contains("instagr.am") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.INSTAGRAM,
                displayName = "Instagram",
                category = SocialCategory.SOCIAL,
                webUrl = normalized,
                appUri = instagramAppUri(normalized)
            )
        }
        host.contains("tiktok.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.TIKTOK,
                displayName = "TikTok",
                category = SocialCategory.SOCIAL,
                webUrl = normalized
            )
        }
        (host.contains("youtube.com") || host.contains("youtu.be")) && !host.contains("music.youtube.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.YOUTUBE,
                displayName = "YouTube",
                category = SocialCategory.SOCIAL,
                webUrl = normalized
            )
        }
        host == "twitter.com" || host.endsWith(".twitter.com") || host == "x.com" || host.endsWith(".x.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.X_TWITTER,
                displayName = "X (Twitter)",
                category = SocialCategory.SOCIAL,
                webUrl = normalized,
                appUri = twitterAppUri(normalized)
            )
        }
        host.contains("facebook.com") || host.contains("fb.com") || host.contains("fb.watch") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.FACEBOOK,
                displayName = "Facebook",
                category = SocialCategory.SOCIAL,
                webUrl = normalized
            )
        }
        host.contains("threads.net") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.THREADS,
                displayName = "Threads",
                category = SocialCategory.SOCIAL,
                webUrl = normalized
            )
        }
        host.contains("snapchat.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.SNAPCHAT,
                displayName = "Snapchat",
                category = SocialCategory.SOCIAL,
                webUrl = normalized
            )
        }
        host.contains("linkedin.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.LINKEDIN,
                displayName = "LinkedIn",
                category = SocialCategory.SOCIAL,
                webUrl = normalized
            )
        }
        host.contains("bsky.app") || host.contains("bsky.social") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.BLUESKY,
                displayName = "Bluesky",
                category = SocialCategory.SOCIAL,
                webUrl = normalized
            )
        }

        // COMMUNITY
        host.contains("discord.gg") || host.contains("discord.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.DISCORD,
                displayName = "Discord",
                category = SocialCategory.COMMUNITY,
                webUrl = normalized
            )
        }
        host.contains("reddit.com") || host.contains("redd.it") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.REDDIT,
                displayName = "Reddit",
                category = SocialCategory.COMMUNITY,
                webUrl = normalized
            )
        }
        host.contains("substack.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.SUBSTACK,
                displayName = "Substack",
                category = SocialCategory.COMMUNITY,
                webUrl = normalized
            )
        }
        host.contains("patreon.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.PATREON,
                displayName = "Patreon",
                category = SocialCategory.COMMUNITY,
                webUrl = normalized
            )
        }
        host.contains("twitch.tv") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.TWITCH,
                displayName = "Twitch",
                category = SocialCategory.COMMUNITY,
                webUrl = normalized
            )
        }
        host.contains("pinterest.com") -> {
            SocialPlatformInfo(
                id = ArtistPlatformIds.PINTEREST,
                displayName = "Pinterest",
                category = SocialCategory.COMMUNITY,
                webUrl = normalized
            )
        }

        else -> null
    }
}

/**
 * Builds an explicit [Intent.ACTION_WEB_SEARCH] querying "$artistName official"
 * for situations where direct social links are unavailable or user wants general discovery.
 *
 * @param artistName Name of the artist to search for.
 * @return Configured web search Intent.
 */
fun buildDefaultSearchIntent(artistName: String): Intent {
    return Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, "$artistName official")
    }
}
