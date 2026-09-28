package com.theveloper.pixelplay.data.service

import androidx.media3.common.MediaItem

internal data class TrustedMediaItemsResolution(
    val mediaItems: MutableList<MediaItem>,
    val trustedArtworkGrantItems: List<MediaItem>,
)

internal fun resolveMediaItemsWithTrustedArtworkGrants(
    requestedItems: List<MediaItem>,
    trustedItemResolver: (String) -> MediaItem?
): TrustedMediaItemsResolution {
    val resolvedItems = ArrayList<MediaItem>(requestedItems.size)
    val trustedArtworkGrantItems = ArrayList<MediaItem>()

    requestedItems.forEach { requestedItem ->
        val trustedItem = trustedItemResolver(requestedItem.mediaId)
        if (trustedItem != null) {
            // Preserve only queue occurrence data. Artwork and source metadata still come
            // exclusively from the trusted library item used for permission grants.
            resolvedItems += if (requestedItem.mediaMetadata.extras?.getString(
                    com.theveloper.pixelplay.data.model.QueueEntryMetadata.ID) != null) {
                com.theveloper.pixelplay.data.model.QueueEntryMetadata.read(requestedItem).attach(trustedItem)
            } else trustedItem
            trustedArtworkGrantItems += trustedItem
        } else {
            // Caller-supplied metadata is untrusted and must never drive provider grants.
            resolvedItems += requestedItem
        }
    }

    return TrustedMediaItemsResolution(
        mediaItems = resolvedItems,
        trustedArtworkGrantItems = trustedArtworkGrantItems
    )
}
