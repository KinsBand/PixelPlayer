package com.theveloper.pixelplay.data.youtube

import java.io.IOException

internal object DownloadIntegrity {
    fun requireComplete(actualBytes: Long, expectedBytes: Long) {
        if (actualBytes <= 0 || (expectedBytes >= 0 && actualBytes != expectedBytes)) {
            throw IOException("Incomplete audio download")
        }
    }
}
