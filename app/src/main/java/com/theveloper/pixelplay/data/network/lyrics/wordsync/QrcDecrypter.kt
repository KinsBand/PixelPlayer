package com.theveloper.pixelplay.data.network.lyrics.wordsync

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Decrypts QQ Music QRC lyrics (hex string -> QQ's Triple-DES variant -> zlib -> UTF-8).
 *
 * QQ Music does not use standard DES: its S-boxes and bit ordering differ from the spec, so
 * javax.crypto cannot be used. This is a Kotlin port of the Apache-2.0 licensed decrypter in
 * Lyricify Lyrics Helper (github.com/WXRIW/Lyricify-Lyrics-Helper, Decrypter/Qrc), which is
 * itself derived from Brad Conte's public-domain DES implementation.
 */
internal object QrcDecrypter {
    private val QQ_KEY = "!@#)(*\$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private const val ENCRYPT = 1
    private const val DECRYPT = 0

    /** Returns the decrypted QRC text, or null when [hex] is not valid encrypted QRC. */
    fun decrypt(hex: String): String? = runCatching {
        val clean = hex.trim()
        require(clean.length >= 16 && clean.length % 16 == 0) { "QRC payload is not whole DES blocks" }
        val encrypted = hexToBytes(clean)
        val decrypted = tripleDes(encrypted, DECRYPT)
        var text = inflate(decrypted).toString(Charsets.UTF_8)
        if (text.startsWith("﻿")) text = text.substring(1)
        text
    }.getOrNull()

    /** Test hook: runs the same cipher in the encrypt direction (no compression). */
    internal fun encryptBlocksForTest(plain: ByteArray): ByteArray = tripleDes(plain, ENCRYPT)
    internal fun decryptBlocksForTest(cipher: ByteArray): ByteArray = tripleDes(cipher, DECRYPT)

    private fun tripleDes(data: ByteArray, mode: Int): ByteArray {
        val schedule = Array(3) { Array(16) { ByteArray(6) } }
        if (mode == ENCRYPT) {
            keySchedule(QQ_KEY.copyOfRange(0, 8), schedule[0], ENCRYPT)
            keySchedule(QQ_KEY.copyOfRange(8, 16), schedule[1], DECRYPT)
            keySchedule(QQ_KEY.copyOfRange(16, 24), schedule[2], ENCRYPT)
        } else {
            keySchedule(QQ_KEY.copyOfRange(0, 8), schedule[2], DECRYPT)
            keySchedule(QQ_KEY.copyOfRange(8, 16), schedule[1], ENCRYPT)
            keySchedule(QQ_KEY.copyOfRange(16, 24), schedule[0], DECRYPT)
        }
        val out = ByteArray(data.size)
        val block = ByteArray(8)
        val temp = ByteArray(8)
        var i = 0
        while (i + 8 <= data.size) {
            System.arraycopy(data, i, block, 0, 8)
            crypt(block, temp, schedule[0])
            crypt(temp, temp, schedule[1])
            crypt(temp, temp, schedule[2])
            System.arraycopy(temp, 0, out, i, 8)
            i += 8
        }
        return out
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                out.write(buffer, 0, n)
                require(out.size() <= MAX_OUTPUT) { "QRC too large" }
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    private const val MAX_OUTPUT = 4 * 1024 * 1024

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Not hex" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    // ── QQ DES variant (unsigned 32-bit arithmetic on Int) ──────────────────

    private fun u(b: Byte): Int = b.toInt() and 0xff

    private fun bitNum(a: ByteArray, b: Int, c: Int): Int =
        ((u(a[b / 32 * 4 + 3 - b % 32 / 8]) ushr (7 - (b % 8))) and 0x01) shl c

    private fun bitNumIntR(a: Int, b: Int, c: Int): Int =
        ((a ushr (31 - b)) and 0x00000001) shl c

    private fun bitNumIntL(a: Int, b: Int, c: Int): Int =
        ((a shl b) and 0x80000000.toInt()) ushr c

    private fun sboxBit(a: Int): Int = (a and 0x20) or ((a and 0x1f) ushr 1) or ((a and 0x01) shl 4)

    private val SBOX1 = intArrayOf(
        14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
        0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
        4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
        15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13
    )
    private val SBOX2 = intArrayOf(
        15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
        3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
        0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
        13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9
    )
    private val SBOX3 = intArrayOf(
        10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
        13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
        13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
        1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12
    )
    private val SBOX4 = intArrayOf(
        7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
        13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
        10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
        3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14
    )
    private val SBOX5 = intArrayOf(
        2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
        14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
        4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
        11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3
    )
    private val SBOX6 = intArrayOf(
        12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
        10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
        9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
        4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13
    )
    private val SBOX7 = intArrayOf(
        4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
        13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
        1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
        6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12
    )
    private val SBOX8 = intArrayOf(
        13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
        1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
        7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
        2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11
    )

    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val KEY_PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17,
        9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35
    )
    private val KEY_PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21,
        13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3
    )
    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9,
        22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47,
        43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31
    )

    private fun keySchedule(key: ByteArray, schedule: Array<ByteArray>, mode: Int) {
        var c = 0
        var d = 0
        var j = 31
        for (i in 0 until 28) { c = c or bitNum(key, KEY_PERM_C[i], j); j-- }
        j = 31
        for (i in 0 until 28) { d = d or bitNum(key, KEY_PERM_D[i], j); j-- }

        for (i in 0 until 16) {
            val shift = KEY_RND_SHIFT[i]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xfffffff0.toInt()
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xfffffff0.toInt()
            val toGen = if (mode == DECRYPT) 15 - i else i
            val row = schedule[toGen]
            for (k in 0 until 6) row[k] = 0
            for (k in 0 until 24) {
                row[k / 8] = (u(row[k / 8]) or bitNumIntR(c, KEY_COMPRESSION[k], 7 - (k % 8))).toByte()
            }
            for (k in 24 until 48) {
                row[k / 8] = (u(row[k / 8]) or bitNumIntR(d, KEY_COMPRESSION[k] - 27, 7 - (k % 8))).toByte()
            }
        }
    }

    private fun ip(state: IntArray, input: ByteArray) {
        val a = intArrayOf(57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3, 61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7)
        val b = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6)
        var s0 = 0
        var s1 = 0
        for (k in 0 until 32) {
            s0 = s0 or bitNum(input, a[k], 31 - k)
            s1 = s1 or bitNum(input, b[k], 31 - k)
        }
        state[0] = s0
        state[1] = s1
    }

    private fun invIp(state: IntArray, out: ByteArray) {
        // Byte order and bit sources exactly as the reference implementation.
        val order = intArrayOf(3, 2, 1, 0, 7, 6, 5, 4)
        for ((n, index) in order.withIndex()) {
            val base = if (n < 4) 7 - n else 3 - (n - 4)
            var v = 0
            v = v or bitNumIntR(state[1], base, 7)
            v = v or bitNumIntR(state[0], base, 6)
            v = v or bitNumIntR(state[1], base + 8, 5)
            v = v or bitNumIntR(state[0], base + 8, 4)
            v = v or bitNumIntR(state[1], base + 16, 3)
            v = v or bitNumIntR(state[0], base + 16, 2)
            v = v or bitNumIntR(state[1], base + 24, 1)
            v = v or bitNumIntR(state[0], base + 24, 0)
            out[index] = v.toByte()
        }
    }

    private fun f(stateIn: Int, key: ByteArray): Int {
        val state = stateIn
        val t1 = bitNumIntL(state, 31, 0) or ((state and 0xf0000000.toInt()) ushr 1) or bitNumIntL(state, 4, 5) or
            bitNumIntL(state, 3, 6) or ((state and 0x0f000000) ushr 3) or bitNumIntL(state, 8, 11) or
            bitNumIntL(state, 7, 12) or ((state and 0x00f00000) ushr 5) or bitNumIntL(state, 12, 17) or
            bitNumIntL(state, 11, 18) or ((state and 0x000f0000) ushr 7) or bitNumIntL(state, 16, 23)
        val t2 = bitNumIntL(state, 15, 0) or ((state and 0x0000f000) shl 15) or bitNumIntL(state, 20, 5) or
            bitNumIntL(state, 19, 6) or ((state and 0x00000f00) shl 13) or bitNumIntL(state, 24, 11) or
            bitNumIntL(state, 23, 12) or ((state and 0x000000f0) shl 11) or bitNumIntL(state, 28, 17) or
            bitNumIntL(state, 27, 18) or ((state and 0x0000000f) shl 9) or bitNumIntL(state, 0, 23)

        val l0 = ((t1 ushr 24) and 0xff) xor u(key[0])
        val l1 = ((t1 ushr 16) and 0xff) xor u(key[1])
        val l2 = ((t1 ushr 8) and 0xff) xor u(key[2])
        val l3 = ((t2 ushr 24) and 0xff) xor u(key[3])
        val l4 = ((t2 ushr 16) and 0xff) xor u(key[4])
        val l5 = ((t2 ushr 8) and 0xff) xor u(key[5])

        var s = (SBOX1[sboxBit(l0 ushr 2)] shl 28) or
            (SBOX2[sboxBit(((l0 and 0x03) shl 4) or (l1 ushr 4))] shl 24) or
            (SBOX3[sboxBit(((l1 and 0x0f) shl 2) or (l2 ushr 6))] shl 20) or
            (SBOX4[sboxBit(l2 and 0x3f)] shl 16) or
            (SBOX5[sboxBit(l3 ushr 2)] shl 12) or
            (SBOX6[sboxBit(((l3 and 0x03) shl 4) or (l4 ushr 4))] shl 8) or
            (SBOX7[sboxBit(((l4 and 0x0f) shl 2) or (l5 ushr 6))] shl 4) or
            SBOX8[sboxBit(l5 and 0x3f)]

        val p = intArrayOf(15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24)
        var permuted = 0
        for (k in 0 until 32) permuted = permuted or bitNumIntL(s, p[k], k)
        s = permuted
        return s
    }

    private fun crypt(input: ByteArray, output: ByteArray, key: Array<ByteArray>) {
        val state = IntArray(2)
        ip(state, input)
        for (idx in 0 until 15) {
            val t = state[1]
            state[1] = f(state[1], key[idx]) xor state[0]
            state[0] = t
        }
        state[0] = f(state[1], key[15]) xor state[0]
        invIp(state, output)
    }
}
