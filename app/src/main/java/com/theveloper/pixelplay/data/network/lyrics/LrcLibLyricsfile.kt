package com.theveloper.pixelplay.data.network.lyrics

import com.theveloper.pixelplay.data.lyrics.Lyricsfile
import com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsParsers

/**
 * The record's Lyricsfile when it is word timed (enough of the song, by the same rule as the
 * other word-timed sources), else null. Only such documents add anything over [LrcLibResponse.syncedLyrics].
 */
internal fun LrcLibResponse.wordTimedLyricsfile(): String? {
    // Line-level documents (almost all of them) have no word lists: skip the YAML parse.
    val raw = lyricsfile?.takeIf { it.contains("words") } ?: return null
    val document = Lyricsfile.parse(raw) ?: return null
    return raw.takeIf { document.hasWordTiming && WordLyricsParsers.isWordTimed(document.lyrics) }
}

/** The richest lyrics in the record: word-timed Lyricsfile, else synced LRC, else plain text. */
internal fun LrcLibResponse.preferredRawLyrics(): String? =
    wordTimedLyricsfile()
        ?: syncedLyrics?.takeIf { it.isNotBlank() }
        ?: plainLyrics?.takeIf { it.isNotBlank() }
