package com.theveloper.pixelplay.data.model

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.theveloper.pixelplay.data.MixQueueMetadata
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable enum class QueueTier { PRIORITY, SESSION }
@Serializable enum class QueueOrigin { MANUAL, GENERATED }

/** Occurrence identity, independent of recording identity and playlist position. */
@Serializable
data class QueueEntryMetadata(
    val entryId: String = UUID.randomUUID().toString(),
    val tier: QueueTier = QueueTier.SESSION,
    val origin: QueueOrigin = QueueOrigin.MANUAL,
    val pinned: Boolean = false,
    val decisionId: String? = null,
    val sessionId: String? = null,
    val recordingId: String? = null,
    val filterPick: Boolean = false,
) {
    fun writeTo(extras: Bundle) {
        extras.putString(ID, entryId)
        extras.putString(TIER, tier.name)
        extras.putBoolean(PINNED, pinned)
        extras.putBoolean(MixQueueMetadata.AUTOMATIC, origin == QueueOrigin.GENERATED)
        extras.putBoolean(MixQueueMetadata.FILTER_PICK, filterPick)
        extras.putString(MixQueueMetadata.DECISION, decisionId)
        extras.putString(MixQueueMetadata.SESSION, sessionId)
        extras.putString(MixQueueMetadata.RECORDING, recordingId)
    }

    fun attach(item: MediaItem): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle())
        writeTo(extras)
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    companion object {
        const val ID = "pixelplay.queue.entryId"
        private const val TIER = "pixelplay.queue.tier"
        const val PINNED = "pixelplay.queue.pinned"
        fun read(item: MediaItem): QueueEntryMetadata {
            val extras = item.mediaMetadata.extras
            return QueueEntryMetadata(
                entryId = extras?.getString(ID) ?: UUID.randomUUID().toString(),
                tier = QueueTier.entries.firstOrNull { it.name == extras?.getString(TIER) } ?: QueueTier.SESSION,
                origin = if (MixQueueMetadata.generated(item)) QueueOrigin.GENERATED else QueueOrigin.MANUAL,
                pinned = extras?.getBoolean(PINNED, false) == true,
                decisionId = extras?.getString(MixQueueMetadata.DECISION),
                sessionId = extras?.getString(MixQueueMetadata.SESSION),
                recordingId = extras?.getString(MixQueueMetadata.RECORDING),
                filterPick = MixQueueMetadata.filterPick(item),
            )
        }
    }
}
