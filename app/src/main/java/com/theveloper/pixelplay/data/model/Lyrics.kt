package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val plain: List<String>? = null,
    val synced: List<SyncedLine>? = null,
    val areFromRemote: Boolean = false,
    val timing: LyricsTimingEvidence? = null
)

@Serializable
data class LyricsTimingEvidence(
    val assetHash: String? = null,
    val lyricsHash: String? = null,
    val source: String = "import",
    val modelVersion: String? = null,
    val language: String? = null,
    val confidence: Float? = null,
    val verified: Boolean = false,
    val offsetMs: Int = 0
)

@Serializable
data class SyncedLine(
    val time: Int,
    val line: String,
    val words: List<SyncedWord>? = null,
    val translation: String? = null,
    val romanization: String? = null,
    val endTime: Int? = null,
    val voiceId: String? = null,
    /** Song section this line belongs to when the source says so (TTML `song-part`). */
    val songPart: String? = null
)

@Serializable
data class SyncedWord(
    val time: Int,
    val word: String,
    val startsNewWord: Boolean = true,
    val endTime: Int? = null,
    val phonemes: List<SyncedPhoneme>? = null,
    val confidence: Float? = null
)

/** Character offsets are UTF-16 ranges within the displayed word, not sound indices. */
@Serializable
data class SyncedPhoneme(
    val phoneme: String,
    val alphabet: String,
    val time: Int,
    val endTime: Int,
    val characterStart: Int,
    val characterEnd: Int,
    val soundType: String = "unknown",
    val confidence: Float? = null,
    val syllableId: String? = null
)
