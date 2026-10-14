package com.theveloper.pixelplay.data.youtube

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

/**
 * Reads a googlevideo stream for ExoPlayer the way YouTube's own clients do (see [YouTubeHttp]):
 * the right user agent and headers, and the transfer split into [YouTubeHttp.CHUNK_BYTES]
 * range requests, because googlevideo throttles or cuts long open-ended responses.
 */
@UnstableApi
class YouTubeChunkedDataSource(private val client: OkHttpClient) : BaseDataSource(/* isNetwork = */ true) {
    private var dataSpec: DataSpec? = null
    private var url: String = ""
    private var position = 0L
    private var totalLength = C.LENGTH_UNSET.toLong()
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var response: Response? = null
    private var stream: InputStream? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        url = dataSpec.uri.toString()
        position = dataSpec.position
        totalLength = YouTubeHttp.contentLengthOf(url) ?: C.LENGTH_UNSET.toLong()
        transferInitializing(dataSpec)
        openChunk()
        opened = true
        transferStarted(dataSpec)
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            totalLength > 0 -> totalLength - position
            else -> C.LENGTH_UNSET.toLong()
        }
        return bytesRemaining
    }

    private fun openChunk() {
        closeChunk()
        val end = if (totalLength > 0) YouTubeHttp.chunkEnd(position, totalLength - 1) else null
        val call = client.newCall(YouTubeHttp.playbackRequest(url, position, end))
        val res = try { call.execute() } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException(e, dataSpec ?: DataSpec(Uri.parse(url)),
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        }
        if (!res.isSuccessful) {
            val code = res.code
            res.close()
            throw HttpDataSource.InvalidResponseCodeException(
                code, null, null, emptyMap(), dataSpec ?: DataSpec(Uri.parse(url)), ByteArray(0)
            )
        }
        if (totalLength <= 0) {
            // "bytes 0-1023/123456"
            res.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { totalLength = it }
        }
        response = res
        stream = res.body.byteStream()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (totalLength > 0 && position >= totalLength) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining > 0) minOf(length.toLong(), bytesRemaining).toInt() else length
        var read = stream?.read(buffer, offset, toRead) ?: -1
        if (read == -1) {
            // This chunk is done; fetch the next one if the file goes on.
            if (totalLength > 0 && position < totalLength) {
                openChunk()
                read = stream?.read(buffer, offset, toRead) ?: -1
            }
            if (read == -1) return C.RESULT_END_OF_INPUT
        }
        position += read
        if (bytesRemaining > 0) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = dataSpec?.uri

    private fun closeChunk() {
        runCatching { stream?.close() }
        runCatching { response?.close() }
        stream = null
        response = null
    }

    override fun close() {
        closeChunk()
        if (opened) {
            opened = false
            transferEnded()
        }
        dataSpec = null
    }

    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = YouTubeChunkedDataSource(client)
    }
}
