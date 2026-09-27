package com.theveloper.pixelplay.data.radio

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackSource

/** Turns radio stations into [Song]s so they play through the normal player, queue and notification. */
object RadioPlayback {
    const val ID_PREFIX = "radio:"
    const val QUEUE_NAME = "Radio"
    private const val HLS_MIME_TYPE = "application/x-mpegURL"

    fun isRadio(song: Song?): Boolean = song?.id?.startsWith(ID_PREFIX) == true

    fun uuidOf(song: Song?): String? = song?.id?.takeIf { it.startsWith(ID_PREFIX) }?.removePrefix(ID_PREFIX)

    fun toSong(station: RadioStation): Song = Song.emptySong().copy(
        id = ID_PREFIX + station.uuid,
        title = station.name,
        artist = station.subtitle.ifBlank { "Internet radio" },
        artistId = station.name.hashCode().toLong(),
        album = listOfNotNull("Radio", station.country).joinToString(" · "),
        albumId = station.uuid.hashCode().toLong(),
        path = station.streamUrl,
        contentUriString = station.streamUrl,
        albumArtUriString = station.favicon,
        // Live: no length. The player shows the stream as it arrives.
        duration = 0L,
        genre = station.tags.firstOrNull()?.replaceFirstChar { it.uppercase() },
        // ExoPlayer only picks the HLS source when told; plain Icecast/Shoutcast is sniffed.
        mimeType = if (station.isHls || station.streamUrl.contains(".m3u8", ignoreCase = true)) HLS_MIME_TYPE else null,
        bitrate = station.bitrate.takeIf { it > 0 }?.times(1000),
        dateAdded = System.currentTimeMillis() / 1000L,
        explicitSource = TrackSource.OTHER,
    )
}
