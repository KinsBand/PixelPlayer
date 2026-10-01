package com.theveloper.pixelplay.data.library

import java.text.Normalizer
import java.util.Locale

/**
 * Normalised keys used to group songs into albums and artists across sources
 * (local files, downloads and songs that are only streamed), plus stable ids for rows the
 * app creates itself. MediaStore ids are small positive numbers, so app-created ids live in
 * their own high ranges and can never collide with them.
 */
object CollectionKeys {
    /** Artists created for downloads: [DOWNLOAD_ARTIST_BASE] + 39-bit hash. */
    const val DOWNLOAD_ARTIST_BASE: Long = 1L shl 40
    /** Albums created for downloads: [DOWNLOAD_ALBUM_BASE] + 39-bit hash. */
    const val DOWNLOAD_ALBUM_BASE: Long = 1L shl 41
    /**
     * Albums/artists that only contain streamed (liked, not downloaded) songs. Negative, so
     * they are recognisable anywhere in the UI and never reach the Room tables' id space.
     */
    const val STREAM_ALBUM_BASE: Long = -(1L shl 42)
    const val STREAM_ARTIST_BASE: Long = -(1L shl 43)

    private const val HASH_MASK: Long = (1L shl 39) - 1

    private val placeholderAlbums = setOf(
        "", "youtube music", "youtube", "spotify", "apple music", "unknown", "unknown album",
        "<unknown>", "other", "music", "single"
    )

    private val editionQualifier = Regex(
        """\s*[(\[]\s*(?:[^)\]]*\b(?:deluxe|remaster(?:ed)?|expanded|anniversary|edition|version|bonus|special|collector'?s?|reissue|mono|stereo|explicit|clean)\b[^)\]]*)[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val releaseSuffix = Regex("""\s*[-–—]\s*(?:single|ep|remastered(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?)\s*$""", RegexOption.IGNORE_CASE)
    private val nonAlnum = Regex("""[^\p{L}\p{N}]+""")
    private val marks = Regex("""\p{Mn}+""")

    /**
     * Artist name without channel decorations: "Drake - Topic", "DrakeVEVO", "Drake (Official)"
     * -> "Drake". Used for display and grouping so one artist never shows up several times.
     */
    fun cleanArtistName(name: String?): String {
        val raw = name.orEmpty().trim()
        if (raw.isEmpty()) return raw
        return runCatching {
            com.theveloper.pixelplay.data.repository.LyricsLookupMetadata.cleanArtist(raw)
        }.getOrDefault(raw).ifBlank { raw }
    }

    /** Lowercase, accents folded, channel suffixes and leading "the " dropped, punctuation collapsed. */
    fun normalizeArtist(name: String?): String {
        val folded = fold(cleanArtistName(name))
        return folded.removePrefix("the ").trim()
    }

    /** Title of the album that holds an artist's downloads with no album information. */
    const val SINGLES_ALBUM_TITLE = "Singles"

    /** Like [normalizeArtist] but also drops edition qualifiers ("(Deluxe)", "- Single"…). */
    fun normalizeAlbum(title: String?): String {
        var t = title.orEmpty()
        repeat(2) { t = editionQualifier.replace(t, "") }
        t = releaseSuffix.replace(t, "")
        return fold(t)
    }

    fun albumKey(albumArtist: String?, album: String?): String =
        normalizeArtist(albumArtist) + "|" + normalizeAlbum(album)

    fun isPlaceholderAlbum(album: String?): Boolean =
        album.isNullOrBlank() || album.trim().lowercase(Locale.ROOT) in placeholderAlbums

    fun downloadArtistId(name: String): Long = DOWNLOAD_ARTIST_BASE + (fnv1a64("a:" + normalizeArtist(name)) and HASH_MASK)

    fun downloadAlbumId(key: String): Long = DOWNLOAD_ALBUM_BASE + (fnv1a64("b:$key") and HASH_MASK)

    fun streamAlbumId(key: String): Long = STREAM_ALBUM_BASE - (fnv1a64("s:$key") and HASH_MASK)

    fun streamArtistId(name: String): Long = STREAM_ARTIST_BASE - (fnv1a64("t:" + normalizeArtist(name)) and HASH_MASK)

    /** Albums that only hold streamed songs (they have no row in the albums table). */
    fun isStreamAlbumId(id: Long): Boolean = id <= STREAM_ALBUM_BASE && id > STREAM_ARTIST_BASE

    fun isStreamArtistId(id: Long): Boolean = id <= STREAM_ARTIST_BASE

    private fun fold(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        return marks.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .let { nonAlnum.replace(it, " ") }
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun fnv1a64(value: String): Long {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (ch in value) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }
}
