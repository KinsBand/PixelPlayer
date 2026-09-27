package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Song


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Accounts : Screen("settings_accounts")
    object SettingsCategory : Screen("settings_category/{categoryId}?highlight={highlight}") {
        fun createRoute(categoryId: String, highlight: String? = null) =
            if (!highlight.isNullOrBlank()) "settings_category/$categoryId?highlight=$highlight"
            else "settings_category/$categoryId"
    }
    object PaletteStyle : Screen("palette_style_settings")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }

    /** Liked songs + all songs, with vibe filters. */
    object YourMusic : Screen("your_music")

    /** One streaming service's playlists (Spotify / YT Music), opened from the Playlists tab. */
    object PlatformPlaylists : Screen("platform_playlists/{platform}") {
        fun createRoute(platform: String) = "platform_playlists/$platform"
    }

    /** Songs you're learning to play: Want → Learning → Finished. */
    object Practice : Screen("practice")
    object  DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object Stats : Screen("stats")
    object GenreDetail : Screen("genre_detail/{genreId}") { // New screen
        fun createRoute(genreId: String) = "genre_detail/$genreId"
    }
    object DJSpace : Screen("dj_space")
    // La ruta base es "album_detail". La ruta completa con el argumento se define en AppNavigation.
    object AlbumDetail : Screen("album_detail/{albumId}") {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
        fun createRoute(albumId: String) = "album_detail/${java.net.URLEncoder.encode(albumId, "UTF-8")}"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
        fun createRoute(artistId: String) = "artist_detail/${java.net.URLEncoder.encode(artistId, "UTF-8")}"

        /** Prefix marking the route argument as an artist *name* rather than a local DB id. */
        const val NAME_PREFIX = "name:"

        /** Opens the artist profile by name — used for online artists that aren't in the local library. */
        fun createRouteForName(artistName: String) = createRoute(NAME_PREFIX + artistName.trim())

        /**
         * Best route for a song's primary artist. Local files use their library artist id; online
         * (YouTube Music / Spotify / Drive) and downloaded songs only carry a name-hash id that doesn't
         * exist in the artists table, so they open the profile by name instead.
         */
        fun createRouteForSong(song: Song, artistName: String? = null): String {
            val name = artistName?.takeIf { it.isNotBlank() }
                ?: song.artists.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                ?: song.artist
            return if (song.isLocal && song.artistId > 0L && artistName == null) {
                createRoute(song.artistId)
            } else {
                createRouteForName(name)
            }
        }
    }

    object EditTransition : Screen("edit_transition?playlistId={playlistId}") {
        fun createRoute(playlistId: String?) =
            if (playlistId != null) "edit_transition?playlistId=$playlistId" else "edit_transition"
    }

    object About : Screen("about")
    object EasterEgg : Screen("easter_egg")

    object ArtistSettings : Screen("artist_settings")
    object DelimiterConfig : Screen("delimiter_config")
    object WordDelimiterConfig : Screen("word_delimiter_config")
    object Equalizer : Screen("equalizer")
    object SpotifyDashboard : Screen("spotify_dashboard")
    object YouTubeMusicDashboard : Screen("ytmusic_dashboard")
    object DeviceCapabilities : Screen("device_capabilities")
}
