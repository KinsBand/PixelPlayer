package com.theveloper.pixelplay.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SongTest {

    @Test
    fun `displayArtistWithGenre formats artist and genre with middle dot`() {
        val song = Song.emptySong().copy(
            artist = "Queen",
            genre = "Rock"
        )
        assertThat(song.displayArtistWithGenre).isEqualTo("Queen · Rock")
    }

    @Test
    fun `displayArtistWithGenre supports multi-artist list and genre`() {
        val song = Song.emptySong().copy(
            artists = listOf(
                ArtistRef(id = 1L, name = "Queen", isPrimary = true),
                ArtistRef(id = 2L, name = "David Bowie", isPrimary = false)
            ),
            genre = "Classic Rock"
        )
        assertThat(song.displayArtistWithGenre).isEqualTo("Queen, David Bowie · Classic Rock")
    }

    @Test
    fun `displayArtistWithGenre returns only artist when genre is null`() {
        val song = Song.emptySong().copy(
            artist = "Queen",
            genre = null
        )
        assertThat(song.displayArtistWithGenre).isEqualTo("Queen")
    }

    @Test
    fun `displayArtistWithGenre returns only artist when genre is blank`() {
        val song = Song.emptySong().copy(
            artist = "Queen",
            genre = "   "
        )
        assertThat(song.displayArtistWithGenre).isEqualTo("Queen")
    }

    @Test
    fun `displayArtistWithGenre ignores unknown genre case-insensitively`() {
        val song1 = Song.emptySong().copy(
            artist = "Queen",
            genre = "Unknown"
        )
        assertThat(song1.displayArtistWithGenre).isEqualTo("Queen")

        val song2 = Song.emptySong().copy(
            artist = "Queen",
            genre = "<unknown>"
        )
        assertThat(song2.displayArtistWithGenre).isEqualTo("Queen")
    }

    @Test
    fun `displayArtistWithGenre trims whitespace around genre`() {
        val song = Song.emptySong().copy(
            artist = "Queen",
            genre = "  Pop Rock  "
        )
        assertThat(song.displayArtistWithGenre).isEqualTo("Queen · Pop Rock")
    }

    @Test
    fun `displayArtistWithGenre returns only genre when artist is blank`() {
        val song = Song.emptySong().copy(
            artist = "",
            genre = "Jazz"
        )
        assertThat(song.displayArtistWithGenre).isEqualTo("Jazz")
    }
}
