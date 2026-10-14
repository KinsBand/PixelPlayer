package com.theveloper.pixelplay.data.search

enum class MediaService {
    SPOTIFY,
    YOUTUBE_MUSIC
}

enum class MediaTargetType {
    ARTIST,
    ALBUM,
    TRACK,
    PLAYLIST
}

data class ParsedMediaUrl(
    val service: MediaService,
    val type: MediaTargetType,
    val id: String,
    val cleanUrl: String
)

object MediaUrlParser {

    private val SPOTIFY_URI_REGEX = Regex(
        """^spotify:(?:user:[^:]+:)?(artist|album|track|playlist):([a-zA-Z0-9_-]+)(?:[?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val SPOTIFY_URL_REGEX = Regex(
        """^(?:https?:\/\/)?(?:open\.)?spotify\.com\/(?:(?:intl-[a-zA-Z0-9_-]+|embed)\/)?(artist|album|track|playlist)\/([a-zA-Z0-9_-]+)(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val YOUTUBE_SHORT_URL_REGEX = Regex(
        """^(?:https?:\/\/)?(?:www\.)?youtu\.be\/([a-zA-Z0-9_-]+)(?:[?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val YOUTUBE_SHORTS_REGEX = Regex(
        """^(?:https?:\/\/)?(?:music\.|www\.|m\.)?youtube\.com\/shorts\/([a-zA-Z0-9_-]+)(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val YOUTUBE_CHANNEL_OR_BROWSE_REGEX = Regex(
        """^(?:https?:\/\/)?(?:music\.|www\.|m\.)?youtube\.com\/(channel|browse|artist)\/([a-zA-Z0-9_-]+)(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val YOUTUBE_HOST_REGEX = Regex(
        """^(?:https?:\/\/)?(?:music\.|www\.|m\.)?youtube\.com\/(?:watch|playlist)(?:[/?#].*)?$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String?): ParsedMediaUrl? {
        if (input.isNullOrBlank()) return null
        val sanitized = sanitizeInput(input)
        if (sanitized.isEmpty()) return null

        // 1. Spotify URI (e.g., spotify:track:3XbsXsdK0ns2J63eomL3YS)
        val spotifyUriMatch = SPOTIFY_URI_REGEX.matchEntire(sanitized)
        if (spotifyUriMatch != null) {
            val typeStr = spotifyUriMatch.groupValues[1].lowercase()
            val id = spotifyUriMatch.groupValues[2]
            val type = when (typeStr) {
                "artist" -> MediaTargetType.ARTIST
                "album" -> MediaTargetType.ALBUM
                "track" -> MediaTargetType.TRACK
                "playlist" -> MediaTargetType.PLAYLIST
                else -> return null
            }
            return ParsedMediaUrl(
                service = MediaService.SPOTIFY,
                type = type,
                id = id,
                cleanUrl = "https://open.spotify.com/$typeStr/$id"
            )
        }

        // 2. Spotify Web URL (e.g., https://open.spotify.com/track/3XbsXsdK0ns2J63eomL3YS)
        val spotifyUrlMatch = SPOTIFY_URL_REGEX.matchEntire(sanitized)
        if (spotifyUrlMatch != null) {
            val typeStr = spotifyUrlMatch.groupValues[1].lowercase()
            val id = spotifyUrlMatch.groupValues[2]
            val type = when (typeStr) {
                "artist" -> MediaTargetType.ARTIST
                "album" -> MediaTargetType.ALBUM
                "track" -> MediaTargetType.TRACK
                "playlist" -> MediaTargetType.PLAYLIST
                else -> return null
            }
            return ParsedMediaUrl(
                service = MediaService.SPOTIFY,
                type = type,
                id = id,
                cleanUrl = "https://open.spotify.com/$typeStr/$id"
            )
        }

        // 3. YouTube Short URL (youtu.be/{id})
        val youTubeShortMatch = YOUTUBE_SHORT_URL_REGEX.matchEntire(sanitized)
        if (youTubeShortMatch != null) {
            val id = youTubeShortMatch.groupValues[1]
            return ParsedMediaUrl(
                service = MediaService.YOUTUBE_MUSIC,
                type = MediaTargetType.TRACK,
                id = id,
                cleanUrl = "https://music.youtube.com/watch?v=$id"
            )
        }

        // 4. YouTube Shorts URL (youtube.com/shorts/{id})
        val youTubeShortsMatch = YOUTUBE_SHORTS_REGEX.matchEntire(sanitized)
        if (youTubeShortsMatch != null) {
            val id = youTubeShortsMatch.groupValues[1]
            return ParsedMediaUrl(
                service = MediaService.YOUTUBE_MUSIC,
                type = MediaTargetType.TRACK,
                id = id,
                cleanUrl = "https://music.youtube.com/watch?v=$id"
            )
        }

        // 5. YouTube Channel / Browse / Artist URL
        val channelMatch = YOUTUBE_CHANNEL_OR_BROWSE_REGEX.matchEntire(sanitized)
        if (channelMatch != null) {
            val id = channelMatch.groupValues[2]
            return ParsedMediaUrl(
                service = MediaService.YOUTUBE_MUSIC,
                type = MediaTargetType.ARTIST,
                id = id,
                cleanUrl = "https://music.youtube.com/channel/$id"
            )
        }

        // 6. YouTube Watch / Playlist URL
        if (YOUTUBE_HOST_REGEX.matches(sanitized)) {
            val (path, queryMap) = extractPathAndQueryParams(sanitized) ?: return null
            when {
                path.endsWith("/watch") || path == "watch" -> {
                    val videoId = queryMap["v"]
                    if (videoId.isNullOrBlank()) return null
                    return ParsedMediaUrl(
                        service = MediaService.YOUTUBE_MUSIC,
                        type = MediaTargetType.TRACK,
                        id = videoId,
                        cleanUrl = "https://music.youtube.com/watch?v=$videoId"
                    )
                }
                path.endsWith("/playlist") || path == "playlist" -> {
                    val listId = queryMap["list"]
                    if (listId.isNullOrBlank()) return null
                    val type = if (listId.startsWith("OLAK5uy_")) {
                        MediaTargetType.ALBUM
                    } else {
                        MediaTargetType.PLAYLIST
                    }
                    return ParsedMediaUrl(
                        service = MediaService.YOUTUBE_MUSIC,
                        type = type,
                        id = listId,
                        cleanUrl = "https://music.youtube.com/playlist?list=$listId"
                    )
                }
            }
        }

        return null
    }

    private fun sanitizeInput(raw: String): String {
        var s = raw.trim()
        while (s.isNotEmpty()) {
            val first = s.first()
            val last = s.last()
            val isQuotedPair = (first == '"' && last == '"') ||
                (first == '\'' && last == '\'') ||
                (first == '“' && last == '”') ||
                (first == '‘' && last == '’') ||
                (first == '«' && last == '»') ||
                (first == '<' && last == '>') ||
                (first == '`' && last == '`')
            if (isQuotedPair && s.length >= 2) {
                s = s.substring(1, s.length - 1).trim()
            } else if (first == '"' || first == '\'' || first == '“' || first == '”' || first == '‘' || first == '’' || first == '`') {
                s = s.substring(1).trim()
            } else if (last == '"' || last == '\'' || last == '“' || last == '”' || last == '‘' || last == '’' || last == '`') {
                s = s.substring(0, s.length - 1).trim()
            } else {
                break
            }
        }
        return s
    }

    private fun extractPathAndQueryParams(url: String): Pair<String, Map<String, String>>? {
        val withoutScheme = url.substringAfter("://", url)
        val pathAndQuery = withoutScheme.substringAfter('/', "")
        val path = pathAndQuery.substringBefore('?').substringBefore('#').trimEnd('/')
        val queryString = if (pathAndQuery.contains('?')) {
            pathAndQuery.substringAfter('?').substringBefore('#')
        } else {
            ""
        }
        val queryMap = mutableMapOf<String, String>()
        if (queryString.isNotEmpty()) {
            for (pair in queryString.split('&')) {
                val parts = pair.split('=', limit = 2)
                if (parts.isNotEmpty()) {
                    val key = parts[0]
                    val value = if (parts.size > 1) parts[1] else ""
                    if (key.isNotEmpty()) {
                        queryMap[key] = value
                    }
                }
            }
        }
        return Pair(path, queryMap)
    }
}
