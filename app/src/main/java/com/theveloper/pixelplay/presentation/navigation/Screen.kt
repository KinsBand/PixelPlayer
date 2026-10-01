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
    object LyricsAnimationStyle : Screen("lyrics_animation_style")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }

    /** Liked songs + all songs, with vibe filters. */
    object YourMusic : Screen("your_music")
    object Friends : Screen("friends")

    /** One streaming service's playlists (Spotify / YT Music), opened from the Playlists tab. */
    object PlatformPlaylists : Screen("platform_playlists/{platform}") {
        fun createRoute(platform: String) = "platform_playlists/$platform"
    }

    /** Songs you're learning to play: Want → Learning → Finished. */
    object Practice : Screen("practice")

    /** Internet radio: local → region → country → world, as a list, tuner dial or map. */
    object Radio : Screen("radio")
    object  DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object RecentlyHeard : Screen("recently_heard")
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
            // Only songs with a library row (numeric id) have a real artist id. Catalogue songs
            // (Spotify / Apple Music, e.g. from a friend's playlist) look "local" until matched
            // to audio, but their artist id is a name hash, so they open by name.
            return if (song.isLibraryRow && song.artistId > 0L && artistName == null) {
                createRoute(song.artistId)
            } else {
                createRouteForName(name)
            }
        }

        /**
         * Route for one of a song's artists, picked by id (song info sheet, artist chips).
         * Library songs keep the id; streamed songs (Spotify, YouTube Music, friends' playlists)
         * only carry a name-hash id that isn't in the artists table, so they open by name.
         */
        fun createRouteForSongArtist(song: Song, artistId: Long): String {
            val name = song.artists.firstOrNull { it.id == artistId }?.name?.takeIf { it.isNotBlank() }
                ?: song.artist.takeIf { artistId == song.artistId && it.isNotBlank() }
            return if ((song.isLibraryRow && artistId > 0L) || name == null) createRoute(artistId) else createRouteForName(name)
        }

        /** Route for an artist row: library artists by id, online / synthetic ones by name. */
        fun createRouteForArtist(artistId: Long, artistName: String, isLibraryArtist: Boolean): String =
            if (isLibraryArtist && artistId > 0L) createRoute(artistId)
            else createRouteForName(artistName)
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

/** True for songs stored in the library tables (MediaStore files and downloads). */
private val Song.isLibraryRow: Boolean
    get() = isLocal && id.toLongOrNull() != null
