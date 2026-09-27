package com.theveloper.pixelplay.data.analysis

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class FileProbeResult(
    val fileSize: Long,
    val sha256Checksum: String?
)

object FileProber {

    fun probe(filePath: String): FileProbeResult {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return FileProbeResult(0L, null)
        }

        val size = file.length()
        val checksum = try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }

        return FileProbeResult(
            fileSize = size,
            sha256Checksum = checksum
        )
    }
}
