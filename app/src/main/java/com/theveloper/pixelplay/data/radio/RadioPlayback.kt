package com.theveloper.pixelplay.data.radio

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackSource
import java.util.concurrent.ConcurrentHashMap

/** Turns radio stations into [Song]s so they play through the normal player, queue and notification. */
object RadioPlayback {
    const val ID_PREFIX = "radio:"
    const val QUEUE_NAME = "Radio"
    private const val HLS_MIME_TYPE = "application/x-mpegURL"

    fun isRadio(song: Song?): Boolean = song?.id?.startsWith(ID_PREFIX) == true

    fun uuidOf(song: Song?): String? = song?.id?.takeIf { it.startsWith(ID_PREFIX) }?.removePrefix(ID_PREFIX)

    /** Every station turned into a song this run, so the player can get its full details back. */
    private val known = ConcurrentHashMap<String, RadioStation>()

    /**
     * The station behind a radio [song], with homepage and location when we have them:
     * this run's stations, then favourites / recents / presets, then what the song itself holds.
     */
    fun stationFor(context: Context, song: Song?): RadioStation? {
        val uuid = uuidOf(song) ?: return null
        known[uuid]?.let { return it }
        RadioStore.get(context).find(uuid)?.let { return it }
        val s = song ?: return null
        return RadioStation(
            uuid = uuid,
            name = s.title,
            streamUrl = s.contentUriString.ifBlank { s.path },
            favicon = s.albumArtUriString,
            tags = listOfNotNull(s.genre),
        )
    }

    fun toSong(station: RadioStation): Song = run {
        // Icecast/Shoutcast streams are recorded while you listen so they can be rewound.
        com.theveloper.pixelplay.data.radio.timeshift.RadioTimeShift.register(
            streamUrl = station.streamUrl,
            bitrateKbps = station.bitrate,
            isHls = station.isHls || station.streamUrl.contains(".m3u8", ignoreCase = true),
        )
        toSongInternal(station)
    }

    private fun toSongInternal(station: RadioStation): Song = Song.emptySong().copy(
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
    ).also { known[station.uuid] = station }
}

/**
 * "Show this station on the radio map", asked from outside the radio screen (the player's map
 * button). Held until the radio screen picks it up, so it survives the navigation.
 */
object RadioMapFocus {
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<RadioStation?>(null)
    val pending: kotlinx.coroutines.flow.StateFlow<RadioStation?> = _pending

    fun request(station: RadioStation) {
        _pending.value = station
    }

    fun consume(): RadioStation? = _pending.value.also { _pending.value = null }
}
