package com.theveloper.pixelplay.data.network.lyrics.wordsync

import androidx.datastore.preferences.core.booleanPreferencesKey

/** Switches for the word-timed lyric sources (Lyrics → Advanced settings → Sources). */
object WordLyricsPrefs {
    /** NetEase YRC, QQ QRC, Kugou KRC and the AMLL TTML DB. On by default. */
    val WORD_SOURCES_ENABLED = booleanPreferencesKey("lyrics_word_sources_enabled_v1")

    /** Musixmatch RichSync via the desktop app token. Off by default (against its terms, rate limited). */
    val MUSIXMATCH_ENABLED = booleanPreferencesKey("lyrics_musixmatch_richsync_v1")

    /**
     * Unison community lyrics (word and line timed; YouTube songs match by video). On by default;
     * its lyrics are always shown with the attribution its ODbL licence requires.
     */
    val UNISON_ENABLED = booleanPreferencesKey("lyrics_unison_enabled_v1")
}
