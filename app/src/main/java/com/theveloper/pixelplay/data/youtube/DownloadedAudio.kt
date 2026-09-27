package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.database.CloudSongEntity
import java.io.File

internal fun CloudSongEntity.downloadedAudioFile(): File? =
    (localFilePath ?: localSongId)?.takeIf { isDownloaded && it.isNotBlank() }?.let(::File)
        ?.takeIf { it.isFile && it.canRead() && it.length() > 0 }
