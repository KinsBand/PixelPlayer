package com.theveloper.pixelplay.data.database

internal fun CloudSongEntity.preserveDownloadedSource(current: CloudSongEntity?): CloudSongEntity {
    val metadata = if (youtubeId == null && current?.youtubeId != null) copy(youtubeId = current.youtubeId) else this
    if (current?.isDownloaded != true || (isDownloaded && !localFilePath.isNullOrBlank())) return metadata
    return metadata.copy(
        isDownloaded = true,
        localFilePath = current.localFilePath,
        localContentUri = current.localContentUri,
        localSongId = current.localSongId
    )
}
