package com.theveloper.pixelplay.data.service.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends requests for the app's own stream proxies (on the loopback address) to [local] and all
 * other requests to [remote], so the two can use different timeouts. A proxy is silent while it
 * reconnects upstream for the player, so the player has to wait longer for it than it would
 * for a remote server.
 */
@UnstableApi
internal class LoopbackRoutingDataSource(
    private val local: DataSource,
    private val remote: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = if (isLoopbackHost(dataSpec.uri.host)) local else remote
        // Set before opening: close() must also release a source whose open() failed.
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        val current = active ?: return
        active = null
        current.close()
    }

    class Factory(
        private val local: DataSource.Factory,
        private val remote: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            LoopbackRoutingDataSource(local.createDataSource(), remote.createDataSource())
    }
}

internal fun isLoopbackHost(host: String?): Boolean = when (host?.lowercase()) {
    "127.0.0.1", "localhost", "::1", "[::1]" -> true
    else -> false
}
