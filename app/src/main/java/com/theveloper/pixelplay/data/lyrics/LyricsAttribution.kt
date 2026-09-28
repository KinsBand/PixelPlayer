package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.network.lyrics.wordsync.UnisonLyricsClient

/**
 * Who to credit under lyrics. Online results record where they came from in
 * [Lyrics.timing]'s source (`online:<provider>`), so the credit names the real provider instead
 * of always LRCLIB.
 */
object LyricsAttribution {
    /**
     * Shown as "[lead] [name]" with [name] linking to [url]. A null [lead] means the app's
     * translated "Lyrics provided by".
     */
    data class Credit(val name: String, val url: String, val lead: String? = null)

    val LRCLIB = Credit("LRCLIB", "https://lrclib.net/")

    /** Worded exactly as Unison's licence requires ("Lyrics from Unison (https://unison.boidu.dev)"). */
    val UNISON = Credit(
        name = UnisonLyricsClient.ATTRIBUTION.removePrefix("Lyrics from "),
        url = UnisonLyricsClient.ATTRIBUTION_URL,
        lead = "Lyrics from"
    )

    private val PROVIDERS = mapOf(
        "lrclib" to LRCLIB,
        "unison" to UNISON,
        "amll" to Credit("AMLL TTML DB", "https://github.com/amll-dev/amll-ttml-db"),
        "musixmatch" to Credit("Musixmatch", "https://www.musixmatch.com/"),
        "qq" to Credit("QQ Music", "https://y.qq.com/"),
        "netease" to Credit("NetEase Cloud Music", "https://music.163.com/"),
        "netease_yrc" to Credit("NetEase Cloud Music", "https://music.163.com/"),
        "kugou" to Credit("Kugou", "https://www.kugou.com/")
    )

    /** The provider key of lyrics fetched online, or null for local, embedded and imported lyrics. */
    fun providerKey(lyrics: Lyrics?): String? =
        lyrics?.timing?.source?.takeIf { it.startsWith(ONLINE_PREFIX) }?.removePrefix(ONLINE_PREFIX)

    /**
     * The credit to show with [lyrics], or null when none is due. Unison is credited whenever
     * its lyrics are shown, stored copies included, as its licence requires. Other providers are
     * credited for the fetch that found them, as before; unknown online results are LRCLIB's.
     */
    fun creditFor(lyrics: Lyrics?): Credit? {
        lyrics ?: return null
        val key = providerKey(lyrics)
        if (key == "unison") return UNISON
        if (!lyrics.areFromRemote) return null
        return key?.let(PROVIDERS::get) ?: LRCLIB
    }

    fun onlineSource(provider: String): String = ONLINE_PREFIX + provider

    private const val ONLINE_PREFIX = "online:"
}
