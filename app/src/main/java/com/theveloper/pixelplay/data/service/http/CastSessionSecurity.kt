package com.theveloper.pixelplay.data.service.http

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import com.theveloper.pixelplay.data.service.cast.CastTokenStore
import com.theveloper.pixelplay.data.service.cast.CastResource

internal data class CastAccessPolicy(
    val allowedClientAddresses: Set<String>,
    val enforceClientAddressAllowlist: Boolean
) {
    companion object {
        val EMPTY = CastAccessPolicy(
            allowedClientAddresses = emptySet(),
            enforceClientAddressAllowlist = false
        )
    }
}

internal object CastSessionSecurity {
    private val loopbackCandidates = setOf(
        "127.0.0.1",
        "::1",
        "0:0:0:0:0:0:0:1",
        "::ffff:127.0.0.1"
    )

    fun buildAccessPolicy(
        castDeviceIpHint: String?,
        serverOwnIp: String? = null
    ): CastAccessPolicy {
        val castAddressVariants = normalizeAddressVariants(castDeviceIpHint)
        val allowedAddresses = buildSet {
            loopbackCandidates.forEach { addAll(normalizeAddressVariants(it)) }
            addAll(castAddressVariants)
            serverOwnIp?.let { addAll(normalizeAddressVariants(it)) }
        }
        return CastAccessPolicy(
            allowedClientAddresses = allowedAddresses,
            enforceClientAddressAllowlist = castAddressVariants.isNotEmpty()
        )
    }

    fun isLoopbackAddress(rawAddress: String?): Boolean {
        val normalized = normalizeAddressVariants(rawAddress)
        return normalized.any { candidate -> candidate in loopbackCandidates }
    }

    fun isAuthorizedClientAddress(remoteAddress: String?, policy: CastAccessPolicy): Boolean {
        if (isLoopbackAddress(remoteAddress)) return true
        if (!policy.enforceClientAddressAllowlist) return true
        if (policy.allowedClientAddresses.isEmpty()) return false
        val normalizedRemote = normalizeAddressVariants(remoteAddress)
        return normalizedRemote.any { candidate -> candidate in policy.allowedClientAddresses }
    }

    fun buildSongUrl(
        serverAddress: String,
        songId: String,
        tokenStore: CastTokenStore
    ): String {
        val token = tokenStore.generateToken(CastResource.Song(songId))
        val baseUrl = requireNotNull(serverAddress.toHttpUrlOrNull()) {
            "Invalid cast server address: $serverAddress"
        }
        return baseUrl
            .newBuilder()
            .encodedPath("/")
            .addPathSegment("song")
            .addPathSegment(token)
            .build()
            .toString()
    }

    fun buildArtUrl(
        serverAddress: String,
        songId: String,
        tokenStore: CastTokenStore
    ): String {
        val token = tokenStore.generateToken(CastResource.Artwork(songId))
        val baseUrl = requireNotNull(serverAddress.toHttpUrlOrNull()) {
            "Invalid cast server address: $serverAddress"
        }
        return baseUrl
            .newBuilder()
            .encodedPath("/")
            .addPathSegment("art")
            .addPathSegment(token)
            .build()
            .toString()
    }

    fun buildLoopbackSongUrl(
        serverAddress: String,
        songId: String,
        tokenStore: CastTokenStore
    ): String? {
        val token = tokenStore.generateToken(CastResource.Song(songId))
        val baseUrl = serverAddress.toHttpUrlOrNull() ?: return null
        return baseUrl
            .newBuilder()
            .host("127.0.0.1")
            .encodedPath("/")
            .addPathSegment("song")
            .addPathSegment(token)
            .build()
            .toString()
    }

    fun redactAuthToken(url: String): String {
        // Since tokens are now path segments in the form /song/{token} or /art/{token},
        // we can redact the last segment of /song/... or /art/...
        val parts = url.split("/")
        if (parts.size >= 2) {
            val last = parts.last()
            val beforeLast = parts[parts.size - 2]
            if (beforeLast == "song" || beforeLast == "art") {
                return url.replace("/$last", "/<redacted>")
            }
        }
        return url
    }

    private fun normalizeAddressVariants(rawAddress: String?): Set<String> {
        val trimmed = rawAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return emptySet()
        val normalized = linkedSetOf(trimmed)
        val parsed = runCatching { InetAddress.getByName(trimmed) }.getOrNull()
        if (parsed != null) {
            parsed.hostAddress?.lowercase()?.let(normalized::add)
            if (parsed.isLoopbackAddress) {
                normalized += loopbackCandidates
            }
        }
        if (trimmed.startsWith("::ffff:")) {
            normalized += trimmed.removePrefix("::ffff:")
        }
        normalized
            .filter { it.startsWith("::ffff:") }
            .forEach { candidate ->
                normalized += candidate.removePrefix("::ffff:")
            }
        return normalized
    }
}
