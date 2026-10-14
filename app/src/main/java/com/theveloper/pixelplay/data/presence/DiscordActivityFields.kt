package com.theveloper.pixelplay.data.presence

/** What goes into the Discord status for one song (pure, so it can be unit-tested). */
internal data class DiscordActivityFields(
    val details: String,
    val state: String,
    val startMs: Long,
    val endMs: Long,
    val largeImage: String?,
    val largeText: String?,
)

private const val DISCORD_TEXT_MAX = 128

/**
 * Title on the first line, artist on the second, a progress bar from the position and length,
 * and the cover when it's a public https address (local files have none Discord can show).
 */
internal fun discordActivityFields(now: NowPlayingPresence, nowMs: Long): DiscordActivityFields {
    val start = nowMs - now.positionMs.coerceAtLeast(0L)
    return DiscordActivityFields(
        details = now.title.trim().take(DISCORD_TEXT_MAX).ifBlank { "Unknown title" },
        state = now.artist.trim().take(DISCORD_TEXT_MAX).ifBlank { "Unknown artist" },
        startMs = start,
        endMs = if (now.durationMs > 0) start + now.durationMs else 0L,
        largeImage = now.artworkUrl?.takeIf { it.startsWith("https://") },
        largeText = now.album?.trim()?.takeIf { it.isNotBlank() }?.take(DISCORD_TEXT_MAX),
    )
}

/** Compiled once (OOM rule). A Discord Application ID is a 17–20 digit number. */
private val DiscordAppIdPattern = Regex("\\d{17,20}")

/** The Application ID typed in the app, or null when it isn't a valid one. */
internal fun parseDiscordApplicationId(input: String): Long? =
    input.trim().takeIf { DiscordAppIdPattern.matches(it) }?.toLongOrNull()

/**
 * Whether the Discord client has to be (re)created before an update: none yet, or one made for
 * another Application ID. [DiscordPresenceManager] sets the current id to null whenever it drops a
 * client (new host Activity, service gone, SDK error), so the next update rebuilds it.
 */
internal fun needsDiscordClientInit(initializedAppId: Long?, appId: Long): Boolean = initializedAppId != appId
