package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.Song
import java.io.File

/** Fast cache revision; unlike an audio SHA-256, this is not proof of audio identity. */
object LyricsAssetRevision {
    fun of(song: Song): String {
        val file = song.path.takeIf { it.isNotBlank() && !it.contains("://") }?.let(::File)
        return LyricsTiming.hash(listOf(song.id, song.contentUriString, song.title, song.artist, song.album,
            song.duration, song.dateModified, song.audioTech.checksum, file?.length(), file?.lastModified()).joinToString("|"))
    }
}
