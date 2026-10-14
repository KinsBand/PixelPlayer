package com.theveloper.pixelplay.data.radio.timeshift

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Plays a recorded radio station from [RadioTimeShiftRecorder]'s segment files: from the
 * requested point (or live), then on, waiting at the end of the recording for new audio the way
 * a network stream waits for the server. Reads block only the player's loading thread.
 */
@UnstableApi
class RadioTimeShiftDataSource(private val recorder: RadioTimeShiftRecorder) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var session: RadioTimeShiftRecorder.Session? = null
    private var position = 0L
    private var opened = false
    private var file: RandomAccessFile? = null
    private var fileSegment = -1L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val s = recorder.sessionFor(RadioTimeShift.baseUrl(dataSpec.uri))
        if (dataSpec.position == 0L) {
            // A fresh start (first play, a rewind or Live): place it in the recording.
            val start = synchronized(s.lock) {
                // A brand-new recording starts at its first byte (the server's opening burst).
                if (s.index.written == 0L) 0L else s.index.startOffsetFor(RadioTimeShift.startMsOf(dataSpec.uri))
            }
            s.itemStartOffset = start
            s.itemStartMs = synchronized(s.lock) { s.index.msAt(start) }
        }
        // A retry after an error resumes where the player got to.
        position = s.itemStartOffset + dataSpec.position
        session = s
        uri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val s = session ?: throw IOException("Not opened")
        var attempts = 0
        while (true) {
            val available = waitForData(s) ?: return C.RESULT_END_OF_INPUT
            val (segment, segmentStart, segmentEnd) = synchronized(s.lock) {
                val sg = s.index.segmentOf(position)
                Triple(sg, s.index.segmentStart(sg), s.index.segmentStart(sg + 1))
            }
            val count = minOf(length.toLong(), available, segmentEnd - position).toInt()
            try {
                val raf = fileFor(s, segment)
                raf.seek(position - segmentStart)
                val n = raf.read(buffer, offset, count)
                if (n <= 0) throw IOException("Short read")
                position += n
                bytesTransferred(n)
                return n
            } catch (e: FileNotFoundException) {
                // The segment was just dropped (past the 60-minute window): skip ahead.
                closeFile()
                if (++attempts > 3) throw e
                synchronized(s.lock) { position = maxOf(position, s.index.firstAvailable) }
            }
        }
    }

    /** Blocks until audio past [position] exists; null when the recording ended. */
    private fun waitForData(s: RadioTimeShiftRecorder.Session): Long? {
        val deadline = System.currentTimeMillis() + STALL_TIMEOUT_MS
        synchronized(s.lock) {
            while (true) {
                if (position < s.index.firstAvailable) position = s.index.firstAvailable
                val available = s.index.written - position
                if (available > 0) return available
                s.failure?.let { throw IOException("Radio stream failed", it) }
                if (s.ended) return null
                if (s.suspended) recorder.resumeIfSuspended(s)
                val wait = deadline - System.currentTimeMillis()
                if (wait <= 0) throw IOException("Radio stream stalled")
                try {
                    s.lock.wait(minOf(wait, WAIT_SLICE_MS))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException()
                }
            }
        }
    }

    private fun fileFor(s: RadioTimeShiftRecorder.Session, segment: Long): RandomAccessFile {
        file?.let { if (fileSegment == segment) return it }
        closeFile()
        return RandomAccessFile(s.segmentFile(segment), "r").also {
            file = it
            fileSegment = segment
        }
    }

    private fun closeFile() {
        runCatching { file?.close() }
        file = null
        fileSegment = -1L
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeFile()
        uri = null
        session = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private companion object {
        const val STALL_TIMEOUT_MS = 25_000L
        const val WAIT_SLICE_MS = 250L
    }
}

/**
 * Sends registered radio streams ([RadioTimeShift.handles]) to [RadioTimeShiftDataSource] and
 * everything else to [upstream], unchanged.
 */
@UnstableApi
class RadioRoutingDataSource(
    private val upstream: DataSource,
    private val createTimeShift: () -> DataSource,
) : DataSource {
    private val listeners = ArrayList<TransferListener>()
    private var timeShift: DataSource? = null
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
        listeners += transferListener
        timeShift?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = if (RadioTimeShift.handles(dataSpec.uri)) {
            timeShift ?: createTimeShift().also { ds ->
                listeners.forEach { ds.addTransferListener(it) }
                timeShift = ds
            }
        } else {
            upstream
        }
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (active ?: throw IOException("Not opened")).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    class Factory(private val context: Context, private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = RadioRoutingDataSource(upstream.createDataSource()) {
            RadioTimeShiftDataSource(RadioTimeShiftRecorder.get(context))
        }
    }
}
