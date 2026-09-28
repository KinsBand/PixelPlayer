package com.theveloper.pixelplay.data.metadata

/** What the online catalogues found for one song (see [SongMetadataGatherer]). Any field may be null. */
data class GatheredMetadata(
    val genre: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val year: Int? = null,
    val bpm: Float? = null,
    val mood: String? = null,
    val moodEstimated: Boolean = false,
    /** Where [mood] came from ("MusicBrainz tags", "Last.fm tags"); null when estimated. */
    val moodSource: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    /** Release date as "yyyy-MM-dd" (or a prefix of it). The original release when known. */
    val releaseDate: String? = null,
    /** Best cover found, already sized to [ArtworkUrls.DEFAULT_SIZE] where the host allows. */
    val coverUrl: String? = null,
    val isrc: String? = null,
    val label: String? = null,
    val upc: String? = null,
    val explicit: Boolean? = null,
    /** Deezer's loudness gain in dB (informational; not used for playback). */
    val gainDb: Float? = null,
    /** Community tags, most used first (MusicBrainz). Used for mood. */
    val tags: List<String> = emptyList(),
    val composer: String? = null,
    val lyricist: String? = null,
    val songwriter: String? = null,
    /** Language of the lyrics as an ISO 639-3 code ("eng"), from the MusicBrainz work. */
    val language: String? = null,
    val recordingMbid: String? = null,
    val releaseMbid: String? = null,
    val workMbid: String? = null,
    /**
     * For fields the sources voted on: the share of the voting weight that agreed with the
     * value kept (1.0 = every source that had the field agreed).
     */
    val agreement: Map<String, Float> = emptyMap(),
    /** Fields the sources disagreed on, e.g. "year: Deezer 2011, MusicBrainz 1975 → 1975". */
    val conflicts: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    /** When the lookup ran; failed lookups are retried after a day. */
    val at: Long = System.currentTimeMillis(),
    /** When MusicBrainz was also asked (0 = not yet); see [SongMetadataGatherer.gatherDeep]. */
    val deepAt: Long = 0L,
    val found: Boolean = true
)
