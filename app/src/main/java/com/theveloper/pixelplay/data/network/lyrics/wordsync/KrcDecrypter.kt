package com.theveloper.pixelplay.data.network.lyrics.wordsync

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater

/**
 * Decrypts Kugou KRC lyrics: base64 -> drop the 4-byte "krc1" header -> XOR with a fixed
 * 16-byte key -> zlib -> UTF-8 (leading BOM removed). The result is Kugou's word-timed text
 * format (`[lineStart,lineDur]<offset,dur,0>word…`), which [com.theveloper.pixelplay.utils.LyricsUtils]
 * already parses.
 */
internal object KrcDecrypter {
    private val KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
    )
    private const val MAX_OUTPUT = 4 * 1024 * 1024

    fun decrypt(base64: String): String? = runCatching {
        val raw = Base64.getDecoder().decode(base64.trim())
        require(raw.size > 4)
        val data = raw.copyOfRange(4, raw.size)
        for (i in data.indices) data[i] = (data[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        var text = inflate(data).toString(Charsets.UTF_8)
        if (text.startsWith("﻿")) text = text.substring(1)
        text
    }.getOrNull()

    /** Test hook: builds a KRC payload from plain text the same way Kugou does. */
    internal fun encryptForTest(text: String): String {
        val deflater = java.util.zip.Deflater()
        deflater.setInput(("﻿" + text).toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val body = out.toByteArray()
        for (i in body.indices) body[i] = (body[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        return Base64.getEncoder().encodeToString("krc1".toByteArray() + body)
    }

    internal fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
                require(out.size() <= MAX_OUTPUT) { "KRC too large" }
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
